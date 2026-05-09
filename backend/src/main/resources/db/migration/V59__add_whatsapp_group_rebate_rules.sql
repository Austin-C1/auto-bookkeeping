ALTER TABLE bookkeeping_whatsapp_groups
    ADD COLUMN rebate_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN rebate_rule VARCHAR(32) NOT NULL DEFAULT 'none';
