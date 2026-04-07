-- ============================================================
-- V20: slot_capacity, dedup_key e tabela de dedup fields
-- Usa SET+PREPARE para não quebrar em dev onde ddl-auto=update
-- já adicionou essas colunas/tabela anteriormente.
-- ============================================================

-- slot_capacity em form_templates
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'slot_capacity') = 0,
    'ALTER TABLE form_templates ADD COLUMN slot_capacity INT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- dedup_key em appointments
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'appointments' AND COLUMN_NAME = 'dedup_key') = 0,
    'ALTER TABLE appointments ADD COLUMN dedup_key VARCHAR(1000) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- tabela auxiliar de campos de deduplicação (CREATE TABLE suporta IF NOT EXISTS no MySQL)
CREATE TABLE IF NOT EXISTS form_template_dedup_fields (
    template_id BIGINT       NOT NULL,
    field_label VARCHAR(255) NOT NULL,
    CONSTRAINT fk_dedup_template FOREIGN KEY (template_id)
        REFERENCES form_templates(id) ON DELETE CASCADE
);
