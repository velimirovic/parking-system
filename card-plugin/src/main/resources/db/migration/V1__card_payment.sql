-- Card plugin: detalji kartičnih plaćanja (CARD_PAYMENT iz "SEP Dizajn Sistema", 3.2).
-- PSP nikada ne prima PAN, ime vlasnika ni CVV, pa ih ova tabela nema.

CREATE SEQUENCE stan_seq START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 999999 CYCLE;

CREATE TABLE card_payment (
    id                    UUID PRIMARY KEY,
    transaction_id        UUID           NOT NULL UNIQUE,     -- PSP_TRANSACTION.id
    merchant_id           VARCHAR(32)    NOT NULL,            -- klijent PSP-a (MRC-...)
    bank_merchant_id      VARCHAR(32)    NOT NULL,            -- MERCHANT_ID iz tabele 2
    stan                  VARCHAR(12)    NOT NULL,
    psp_timestamp         TIMESTAMPTZ    NOT NULL,
    amount                NUMERIC(19, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    return_url            VARCHAR(1000)  NOT NULL,
    bank_payment_id       VARCHAR(32)    UNIQUE,
    bank_payment_url      VARCHAR(1000),
    status                VARCHAR(16)    NOT NULL CHECK (status IN
                              ('INITIATING', 'PENDING', 'APPROVED', 'DECLINED', 'EXPIRED', 'ERROR')),
    response_code         VARCHAR(2),
    global_transaction_id VARCHAR(40),
    acquirer_timestamp    TIMESTAMPTZ,
    core_notified_at      TIMESTAMPTZ,
    core_attempts         INT            NOT NULL DEFAULT 0,
    next_core_attempt_at  TIMESTAMPTZ,
    next_status_check_at  TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ux_card_payment_trace UNIQUE (bank_merchant_id, stan, psp_timestamp)
);
CREATE INDEX ix_card_payment_core ON card_payment (next_core_attempt_at)
    WHERE core_notified_at IS NULL AND status IN ('APPROVED', 'DECLINED', 'EXPIRED', 'ERROR');
CREATE INDEX ix_card_payment_poll ON card_payment (next_status_check_at) WHERE status = 'PENDING';
