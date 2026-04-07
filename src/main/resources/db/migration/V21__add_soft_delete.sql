-- ============================================================
-- V21: soft delete nas tabelas form_templates e clients
-- deleted = 0 (FALSE) → ativo   |   deleted = 1 (TRUE) → excluído
-- Rows existentes recebem DEFAULT 0 (continuam visíveis).
-- ============================================================

-- deleted em form_templates
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'deleted') = 0,
    'ALTER TABLE form_templates ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- deleted em clients
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clients' AND COLUMN_NAME = 'deleted') = 0,
    'ALTER TABLE clients ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
