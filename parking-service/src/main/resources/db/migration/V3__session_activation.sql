-- Aktiviranje parking sesije: potvrda placanja od PSP-a (webhook + provera statusa) i refundacija.

ALTER TABLE payment_transaction
    -- trenutak kada je banka odobrila placanje (ACQUIRER_TIMESTAMP) ili kada je parking sistem primio potvrdu
    ADD COLUMN confirmed_at        TIMESTAMPTZ,
    -- poslednja provera statusa na PSP-u (reconciliation)
    ADD COLUMN last_checked_at     TIMESTAMPTZ,
    ADD COLUMN check_attempts      INT NOT NULL DEFAULT 0,
    -- placanje je uspelo, a sesija nije mogla biti aktivirana: novac se vraca istim nacinom placanja
    ADD COLUMN refund_status       VARCHAR(20),
    ADD COLUMN refund_reason       VARCHAR(200),
    ADD COLUMN refund_requested_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_payment_refund_status CHECK (refund_status IS NULL OR refund_status IN ('REQUIRED', 'REFUNDED'));

CREATE INDEX ix_payment_open_check ON payment_transaction (status, last_checked_at)
    WHERE status IN ('INIT', 'PENDING');
CREATE INDEX ix_payment_refund ON payment_transaction (refund_status) WHERE refund_status IS NOT NULL;

-- Svaki primljeni webhook PSP-a (i neispravan) - trag za reviziju (PCI DSS 10).
CREATE TABLE psp_webhook_event (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id        VARCHAR(100),
    event              VARCHAR(40),
    merchant_order_id  VARCHAR(64),
    psp_status         VARCHAR(20),
    signature_valid    BOOLEAN      NOT NULL,
    -- ishod obrade: APPLIED, DUPLICATE, IGNORED, REJECTED, ...
    outcome            VARCHAR(40)  NOT NULL,
    detail             VARCHAR(300),
    received_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_webhook_order ON psp_webhook_event (merchant_order_id, received_at);
