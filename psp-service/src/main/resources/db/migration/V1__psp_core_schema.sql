-- PSP - osnovna sema (model iz "SEP Dizajn Sistema", poglavlje 3)

-- ---------- Merchanti (klijenti PSP-a) ----------
CREATE SEQUENCE merchant_number_seq START WITH 42;

CREATE TABLE merchant (
    id                       UUID PRIMARY KEY,
    name                     VARCHAR(200) NOT NULL,
    merchant_id              VARCHAR(32)  NOT NULL UNIQUE,      -- MERCHANT_ID iz tabele 1
    api_key_hash             VARCHAR(64)  NOT NULL UNIQUE,      -- SHA-256, kljuc se ne cuva
    api_key_last4            VARCHAR(4)   NOT NULL,
    webhook_secret_encrypted TEXT         NOT NULL,             -- AES-GCM
    webhook_secret_last4     VARCHAR(4)   NOT NULL,
    webhook_url              VARCHAR(500),
    success_url              VARCHAR(500),
    failed_url               VARCHAR(500),
    error_url                VARCHAR(500),
    rate_limit_per_sec       INT          NOT NULL DEFAULT 50 CHECK (rate_limit_per_sec > 0),
    status                   VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0
);

-- ---------- Korisnici portala ----------
CREATE TABLE psp_user (
    id                    UUID PRIMARY KEY,
    email                 VARCHAR(254) NOT NULL,
    full_name             VARCHAR(200) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL CHECK (role IN ('SUPER_ADMIN', 'MERCHANT_ADMIN')),
    merchant_id           UUID REFERENCES merchant (id),
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    mfa_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret_encrypted  TEXT,
    mfa_last_used_step    BIGINT,                              -- zastita od ponovne upotrebe TOTP koda
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_psp_user_merchant CHECK (
        (role = 'SUPER_ADMIN' AND merchant_id IS NULL) OR
        (role = 'MERCHANT_ADMIN' AND merchant_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_psp_user_email ON psp_user (LOWER(email));

-- Poslednje lozinke - nova lozinka ne sme biti ista kao poslednje 4 (PCI DSS 8.3.7)
CREATE TABLE password_history (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES psp_user (id),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_password_history_user ON password_history (user_id, created_at DESC);

-- ---------- Metode placanja (plugin-ovi) ----------
CREATE TABLE payment_method (
    id               UUID PRIMARY KEY,
    code             VARCHAR(32)  NOT NULL UNIQUE,   -- CARD, QR, PAYPAL, CRYPTO... namerno nije enum
    display_name     VARCHAR(100) NOT NULL,
    plugin_base_url  VARCHAR(500) NOT NULL,
    supports_refund  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE TABLE merchant_payment_method (
    id                UUID PRIMARY KEY,
    merchant_id       UUID        NOT NULL REFERENCES merchant (id),
    payment_method_id UUID        NOT NULL REFERENCES payment_method (id),
    config_encrypted  TEXT,                          -- npr. bank MERCHANT_ID, racun, PayPal client_id, wallet
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_merchant_payment_method UNIQUE (merchant_id, payment_method_id)
);

INSERT INTO payment_method (id, code, display_name, plugin_base_url, supports_refund, is_active, created_at) VALUES
    (gen_random_uuid(), 'CARD',   'Platna kartica',  'http://card-plugin:9101',   TRUE, TRUE, NOW()),
    (gen_random_uuid(), 'QR',     'IPS QR kod',      'http://qr-plugin:9102',     TRUE, TRUE, NOW() + INTERVAL '1 second'),
    (gen_random_uuid(), 'PAYPAL', 'PayPal',          'http://paypal-plugin:9103', TRUE, TRUE, NOW() + INTERVAL '2 seconds'),
    (gen_random_uuid(), 'CRYPTO', 'Kriptovaluta',    'http://crypto-plugin:9104', TRUE, TRUE, NOW() + INTERVAL '3 seconds');

-- ---------- Transakcije ----------
CREATE TABLE psp_transaction (
    id                    UUID PRIMARY KEY,
    merchant_id           UUID          NOT NULL REFERENCES merchant (id),
    payment_method_id     UUID REFERENCES payment_method (id),   -- bira se posle INIT
    merchant_order_id     VARCHAR(64)   NOT NULL,
    merchant_timestamp    TIMESTAMPTZ   NOT NULL,
    amount                NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency              VARCHAR(3)    NOT NULL,
    status                VARCHAR(16)   NOT NULL CHECK (status IN
                              ('INIT', 'PENDING', 'SUCCESS', 'FAILED', 'ERROR', 'EXPIRED', 'REFUNDED')),
    stan                  VARCHAR(12),
    psp_timestamp         TIMESTAMPTZ,
    global_transaction_id VARCHAR(100),
    acquirer_timestamp    TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ux_psp_transaction_order UNIQUE (merchant_id, merchant_order_id)
);
CREATE INDEX ix_psp_transaction_merchant_created ON psp_transaction (merchant_id, created_at DESC);

CREATE TABLE webhook_delivery (
    id               UUID PRIMARY KEY,
    transaction_id   UUID        NOT NULL REFERENCES psp_transaction (id),
    event_type       VARCHAR(32) NOT NULL,
    payload          TEXT        NOT NULL,
    attempt_count    INT         NOT NULL DEFAULT 0,
    max_attempts     INT         NOT NULL DEFAULT 8,
    next_attempt_at  TIMESTAMPTZ,
    status           VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    last_http_status INT,
    last_duration_ms INT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_webhook_delivery_tx ON webhook_delivery (transaction_id);
CREATE INDEX ix_webhook_delivery_pending ON webhook_delivery (status, next_attempt_at);

-- ---------- Audit log (PCI DSS zahtev 10) ----------
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    seq           BIGSERIAL    NOT NULL UNIQUE,      -- redosled u lancu
    occurred_at   TIMESTAMPTZ  NOT NULL,
    actor_type    VARCHAR(20)  NOT NULL,             -- PSP_USER | MERCHANT_API | SYSTEM | PLUGIN
    actor_id      VARCHAR(254),
    action        VARCHAR(40)  NOT NULL,
    resource_type VARCHAR(40),
    resource_id   VARCHAR(100),
    source_ip     VARCHAR(45),
    outcome       VARCHAR(10)  NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    details       TEXT,                              -- bez osetljivih podataka
    prev_hash     VARCHAR(64)  NOT NULL,
    hash          VARCHAR(64)  NOT NULL
);
CREATE INDEX ix_audit_log_occurred ON audit_log (occurred_at DESC);

-- Audit log je append-only: izmena i brisanje su zabranjeni na nivou baze
CREATE FUNCTION audit_log_block_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log je append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_change();
