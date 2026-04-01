SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'form_templates'
      AND COLUMN_NAME  = 'has_attendance'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE form_templates ADD COLUMN has_attendance BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
