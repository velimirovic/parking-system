-- Parking sistem - osnovni model podataka
-- Vremena se cuvaju kao TIMESTAMPTZ (UTC), a pravila naplate u lokalnom vremenu (Europe/Belgrade).

-- ---------- korisnici ----------
CREATE TABLE app_user (
    id                     UUID PRIMARY KEY,
    email                  VARCHAR(254) NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    first_name             VARCHAR(60)  NOT NULL,
    last_name              VARCHAR(60)  NOT NULL,
    phone                  VARCHAR(30),
    -- koliko minuta pre isteka se salje obavestenje (podrazumevano 5)
    notify_before_minutes  INT          NOT NULL DEFAULT 5 CHECK (notify_before_minutes BETWEEN 1 AND 60),
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_app_user_email UNIQUE (email)
);

-- ---------- vozila ----------
CREATE TABLE vehicle (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id),
    -- normalizovana oznaka (bez razmaka i crtica, velika slova) - za proveru jedinstvenosti
    plate          VARCHAR(16) NOT NULL,
    -- oznaka za prikaz, npr. "BG 123-AB"
    display_plate  VARCHAR(20) NOT NULL,
    brand          VARCHAR(40),
    model          VARCHAR(40),
    is_default     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- "meko" brisanje: uklonjeno vozilo ostaje vidljivo u istoriji
    deleted_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_vehicle_user_plate ON vehicle (user_id, plate) WHERE deleted_at IS NULL;
CREATE INDEX ix_vehicle_user ON vehicle (user_id);

-- ---------- zone, cenovnici, lokacije ----------
CREATE TABLE parking_zone (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(20)  NOT NULL UNIQUE,
    name                   VARCHAR(60)  NOT NULL,
    area                   VARCHAR(120) NOT NULL,
    description            VARCHAR(400),
    latitude               NUMERIC(9, 6),
    longitude              NUMERIC(9, 6),
    currency               VARCHAR(3)      NOT NULL DEFAULT 'RSD',
    min_duration_minutes   INT          NOT NULL CHECK (min_duration_minutes > 0),
    max_duration_minutes   INT          NOT NULL,
    duration_step_minutes  INT          NOT NULL DEFAULT 15 CHECK (duration_step_minutes > 0),
    extension_allowed      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- NULL = neograniceno
    max_extensions         INT CHECK (max_extensions IS NULL OR max_extensions >= 0),
    -- najduze ukupno trajanje sesije sa produzenjima; NULL = bez ogranicenja
    max_total_minutes      INT,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order             INT          NOT NULL DEFAULT 0,
    CHECK (max_duration_minutes >= min_duration_minutes),
    CHECK (max_total_minutes IS NULL OR max_total_minutes >= max_duration_minutes)
);

-- Radno vreme naplate i cena po satu. Dani: 1 = ponedeljak ... 7 = nedelja (ISO-8601).
-- Vreme je u minutima od ponoci (0-1440), interval je [start_minute, end_minute).
-- Dan/vreme bez tarife = nema naplate, pa se sesija tada ne moze pokrenuti.
CREATE TABLE zone_tariff (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zone_id         BIGINT        NOT NULL REFERENCES parking_zone (id),
    label           VARCHAR(40)   NOT NULL,
    day_from        SMALLINT      NOT NULL CHECK (day_from BETWEEN 1 AND 7),
    day_to          SMALLINT      NOT NULL CHECK (day_to BETWEEN 1 AND 7),
    start_minute    INT           NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    end_minute      INT           NOT NULL CHECK (end_minute BETWEEN 1 AND 1440),
    price_per_hour  NUMERIC(10, 2) NOT NULL CHECK (price_per_hour > 0),
    CHECK (day_to >= day_from),
    CHECK (end_minute > start_minute)
);
CREATE INDEX ix_zone_tariff_zone ON zone_tariff (zone_id);

CREATE TABLE parking_location (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zone_id   BIGINT       NOT NULL REFERENCES parking_zone (id),
    name      VARCHAR(80)  NOT NULL,
    address   VARCHAR(160),
    capacity  INT
);
CREATE INDEX ix_parking_location_zone ON parking_location (zone_id);

-- ---------- parking sesije ----------
-- PENDING_PAYMENT -> ACTIVE -> EXPIRED
-- PENDING_PAYMENT -> CANCELLED (placanje neuspesno / isteklo)
CREATE TABLE parking_session (
    id                UUID PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES app_user (id),
    vehicle_id        UUID          NOT NULL REFERENCES vehicle (id),
    zone_id           BIGINT        NOT NULL REFERENCES parking_zone (id),
    location_id       BIGINT REFERENCES parking_location (id),
    -- registarska oznaka u trenutku kupovine (vozilo moze kasnije biti izmenjeno)
    plate             VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    duration_minutes  INT           NOT NULL CHECK (duration_minutes > 0),
    -- planirani period iz obracuna; pri aktivaciji se postavlja stvarni period
    starts_at         TIMESTAMPTZ   NOT NULL,
    ends_at           TIMESTAMPTZ   NOT NULL,
    -- ukupno placeno (kupovina + produzenja)
    amount            NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency          VARCHAR(3)       NOT NULL,
    extension_count   INT           NOT NULL DEFAULT 0,
    activated_at      TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0,
    CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_session_user_created ON parking_session (user_id, created_at DESC);
CREATE INDEX ix_session_vehicle_status ON parking_session (vehicle_id, status);
CREATE INDEX ix_session_status_ends ON parking_session (status, ends_at);

-- ---------- platne transakcije (kupovina i produzenja) ----------
CREATE TABLE payment_transaction (
    id                     UUID PRIMARY KEY,
    session_id             UUID          NOT NULL REFERENCES parking_session (id),
    user_id                UUID          NOT NULL REFERENCES app_user (id),
    -- MERCHANT_ORDER_ID koji parking sistem salje PSP-u
    merchant_order_id      VARCHAR(64)   NOT NULL,
    purpose                VARCHAR(20)   NOT NULL,
    duration_minutes       INT           NOT NULL,
    amount                 NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)       NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    -- kljuc koji frontend salje uz zahtev (dvostruki klik vraca isti nalog)
    idempotency_key        VARCHAR(64)   NOT NULL,
    psp_transaction_id     VARCHAR(64),
    payment_method         VARCHAR(20),
    global_transaction_id  VARCHAR(64),
    acquirer_timestamp     TIMESTAMPTZ,
    redirect_url           VARCHAR(500),
    payment_expires_at     TIMESTAMPTZ,
    failure_reason         VARCHAR(200),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_payment_merchant_order UNIQUE (merchant_order_id),
    CONSTRAINT ux_payment_idempotency UNIQUE (user_id, idempotency_key),
    CHECK (purpose IN ('PURCHASE', 'EXTENSION')),
    CHECK (status IN ('INIT', 'PENDING', 'SUCCESS', 'FAILED', 'ERROR', 'EXPIRED', 'REFUNDED'))
);
CREATE INDEX ix_payment_session ON payment_transaction (session_id);
CREATE INDEX ix_payment_status ON payment_transaction (status);

-- ---------- trag promena statusa sesije (ne menja se, samo dodaje) ----------
CREATE TABLE session_status_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES parking_session (id),
    transaction_id  UUID REFERENCES payment_transaction (id),
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)  NOT NULL,
    event           VARCHAR(40)  NOT NULL,
    note            VARCHAR(200),
    -- ko je izazvao promenu: USER, PSP, SYSTEM
    actor           VARCHAR(20)  NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_status_log_session ON session_status_log (session_id, occurred_at);
