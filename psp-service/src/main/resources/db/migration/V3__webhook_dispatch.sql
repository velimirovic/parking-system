-- Slanje webhook-ova iz outbox-a

-- Demo zapisi (DemoDataSeeder) samo ilustruju portal i nikada se ne salju na adrese demo klijenata
ALTER TABLE webhook_delivery ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE webhook_delivery SET simulated = TRUE;
