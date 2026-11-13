-- Banka (Acquirer = Issuer) - model iz "SEP Dizajn Sistema", poglavlje 4

-- ---------- Klijenti banke i racuni ----------
CREATE TABLE bank_customer (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,          -- prijava u mobilnu aplikaciju (IPS)
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE account (
    id             UUID PRIMARY KEY,
    customer_id    UUID           REFERENCES bank_customer (id),   -- null za racune prodavaca
    account_number VARCHAR(20)    NOT NULL UNIQUE,                 -- format 3-13-2, kontrolni broj mod 97
    holder_name    VARCHAR(200)   NOT NULL,
    balance        NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    reserved       NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    currency       VARCHAR(3)     NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL,
    -- raspolozivo stanje nikad ne sme biti negativno (zastita i na nivou baze)
    CONSTRAINT chk_account_available CHECK (balance - reserved >= 0)
);

-- ---------- Platne kartice (PCI DSS zahtev 3) ----------
-- PAN se ne cuva u citljivom obliku: pan_token (HMAC) sluzi za pretragu, pan_encrypted (AES-GCM) za prikaz
-- poslednje 4 cifre i eventualno desifrovanje, CVV se cuva samo kao HMAC (simulacija izdavaoca kartice).
CREATE TABLE card (
    id               UUID PRIMARY KEY,
    account_id       UUID         NOT NULL REFERENCES account (id),
    pan_token        VARCHAR(64)  NOT NULL UNIQUE,
    pan_encrypted    TEXT         NOT NULL,
    pan_last4        VARCHAR(4)   NOT NULL,
    brand            VARCHAR(16)  NOT NULL CHECK (brand IN ('VISA', 'MASTERCARD', 'DINACARD')),
    expiry_month     SMALLINT     NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year      SMALLINT     NOT NULL,                      -- cetvorocifrena godina
    card_holder_name VARCHAR(100) NOT NULL,
    cvv_hash         VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_card_account ON card (account_id);

-- ---------- Prodavci (ugovoreni trgovci PSP-a kod banke) ----------
CREATE TABLE acquirer_merchant (
    id          UUID PRIMARY KEY,
    merchant_id VARCHAR(32)  NOT NULL UNIQUE,     -- MERCHANT_ID iz tabele 2 (npr. BNK-PSP-007781)
    name        VARCHAR(200) NOT NULL,
    account_id  UUID         NOT NULL REFERENCES account (id),
    status      VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- ---------- Kursna lista banke (placanje u stranoj valuti sa dinarskog racuna) ----------
CREATE TABLE exchange_rate (
    currency    VARCHAR(3)     PRIMARY KEY,
    rate_to_rsd NUMERIC(19, 6) NOT NULL CHECK (rate_to_rsd > 0),
    updated_at  TIMESTAMPTZ    NOT NULL
);

-- ---------- Zahtev za placanje (jedna forma = jedna transakcija) ----------
CREATE TABLE payment_request (
    id                  UUID PRIMARY KEY,
    payment_id          VARCHAR(32)    NOT NULL UNIQUE,      -- PAYMENT_ID, deo PAYMENT_URL-a
    merchant_id         VARCHAR(32)    NOT NULL,              -- MERCHANT_ID PSP-a kod banke
    stan                VARCHAR(12)    NOT NULL,
    psp_timestamp       TIMESTAMPTZ    NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)     NOT NULL,
    merchant_account_id UUID           NOT NULL REFERENCES account (id),
    result_url          VARCHAR(1000)  NOT NULL,              -- povratak kupca na PSP
    notify_url          VARCHAR(1000)  NOT NULL,              -- obavestenje PSP-u (card plugin)
    expires_at          TIMESTAMPTZ    NOT NULL,              -- forma je vremenski ogranicena
    attempt_used        BOOLEAN        NOT NULL DEFAULT FALSE, -- samo jedan pokusaj placanja
    status              VARCHAR(16)    NOT NULL CHECK (status IN ('CREATED', 'COMPLETED', 'FAILED', 'EXPIRED')),
    response_code       VARCHAR(2),
    psp_notified_at     TIMESTAMPTZ,
    notify_attempts     INT            NOT NULL DEFAULT 0,
    next_notify_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL,
    completed_at        TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    -- PSP moze da proveri status po MERCHANT_ID + STAN + PSP_TIMESTAMP, a dupli zahtev se odbija
    CONSTRAINT ux_payment_request_trace UNIQUE (merchant_id, stan, psp_timestamp)
);
CREATE INDEX ix_payment_request_open ON payment_request (expires_at) WHERE status = 'CREATED';
CREATE INDEX ix_payment_request_notify ON payment_request (next_notify_at)
    WHERE psp_notified_at IS NULL AND status <> 'CREATED';

-- ---------- Bankarske transakcije ----------
CREATE TABLE bank_transaction (
    id                    UUID PRIMARY KEY,
    payment_request_id    UUID           REFERENCES payment_request (id),
    payer_account_id      UUID           REFERENCES account (id),         -- null ako kartica nije pronadjena
    payee_account_id      UUID           NOT NULL REFERENCES account (id),
    global_transaction_id VARCHAR(40)    NOT NULL UNIQUE,
    amount                NUMERIC(19, 2) NOT NULL,                        -- u valuti zahteva
    currency              VARCHAR(3)     NOT NULL,
    debit_amount          NUMERIC(19, 2),                                 -- u valuti racuna kupca
    debit_currency        VARCHAR(3),
    exchange_rate         NUMERIC(19, 6),
    type                  VARCHAR(16)    NOT NULL CHECK (type IN ('CARD', 'QR', 'REFUND')),
    status                VARCHAR(16)    NOT NULL CHECK (status IN ('RESERVED', 'DECLINED', 'REFUNDED')),
    response_code         VARCHAR(2)     NOT NULL,
    card_last4            VARCHAR(4),
    acquirer_timestamp    TIMESTAMPTZ    NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL
);
CREATE UNIQUE INDEX ux_bank_transaction_request ON bank_transaction (payment_request_id)
    WHERE type <> 'REFUND';

INSERT INTO exchange_rate (currency, rate_to_rsd, updated_at) VALUES
    ('RSD', 1.000000, NOW()),
    ('EUR', 117.150000, NOW()),
    ('USD', 108.420000, NOW()),
    ('CHF', 124.870000, NOW()),
    ('GBP', 139.630000, NOW());
