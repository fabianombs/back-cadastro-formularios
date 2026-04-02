-- Adiciona coluna col_span em form_fields (2 = largura total, 1 = meia largura)
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_fields' AND COLUMN_NAME = 'col_span') = 0,
    'ALTER TABLE form_fields ADD COLUMN col_span INT NOT NULL DEFAULT 2',
    'SELECT 1'
);
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
