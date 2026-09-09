-- Provider delivery ids already processed.
--
-- This is what makes webhook handling idempotent: providers retry a delivery they believe failed,
-- and without this table a retry would run the whole materialization pipeline a second time.
CREATE TABLE source.webhook_deliveries (
    id            UUID         NOT NULL,
    repository_id UUID         NOT NULL,
    delivery_id   VARCHAR(200) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_webhook_deliveries_repository
        FOREIGN KEY (repository_id) REFERENCES source.repositories (id) ON DELETE CASCADE,
    CONSTRAINT uq_webhook_deliveries UNIQUE (repository_id, delivery_id)
);
