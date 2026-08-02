-- Coluna exigida pela entidade FormTemplate (attendanceColumnOrder) que nunca
-- teve migration: foi criada direto em producao, fora do Flyway. Sem ela, um
-- ambiente construido do zero pelas migrations nao sobe (ddl-auto=validate
-- falha com "missing column [attendance_column_order] in table [form_templates]").
--
-- IDEMPOTENTE de proposito: em producao a coluna ja existe, e um ALTER ADD
-- COLUMN simples abortaria o deploy com "Duplicate column name". O MySQL nao
-- tem ADD COLUMN IF NOT EXISTS, entao a checagem e feita no information_schema.
SET @existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'form_templates'
      AND column_name  = 'attendance_column_order'
);

SET @ddl := IF(@existe = 0,
    'ALTER TABLE form_templates ADD COLUMN attendance_column_order TEXT NULL',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
