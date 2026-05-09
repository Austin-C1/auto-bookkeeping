ALTER TABLE bookkeeping_whatsapp_groups
    ADD COLUMN source_chat_id VARCHAR(128) NULL,
    ADD COLUMN currency VARCHAR(16) NOT NULL DEFAULT 'USDT',
    ADD COLUMN exchange_rate DECIMAL(18, 6) NOT NULL DEFAULT 1.000000,
    ADD COLUMN rebate_points DECIMAL(10, 4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN last_scanned_message_id VARCHAR(128) NULL,
    ADD INDEX idx_bookkeeping_whatsapp_groups_source_chat (source_chat_id);
