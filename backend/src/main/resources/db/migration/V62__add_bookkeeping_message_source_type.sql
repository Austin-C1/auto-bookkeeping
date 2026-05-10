ALTER TABLE bookkeeping_whatsapp_groups
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'whatsapp' AFTER group_key,
    ADD INDEX idx_bookkeeping_whatsapp_groups_source_type (source_type),
    DROP INDEX uk_bookkeeping_whatsapp_group_key,
    ADD UNIQUE KEY uk_bookkeeping_message_group_source_key (source_type, group_key);

ALTER TABLE bookkeeping_whatsapp_orders
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'whatsapp' AFTER group_id,
    ADD INDEX idx_bookkeeping_whatsapp_orders_source_type (source_type);
