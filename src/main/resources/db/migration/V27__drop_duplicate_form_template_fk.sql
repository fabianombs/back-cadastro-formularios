-- Remove FK auto-gerada pelo Hibernate (ddl-auto=update) em ambientes que
-- acabaram com duas FKs apontando para clients(id) em form_templates.client_id:
--   - fk_form_template_client (nomeada, correta após V26)
--   - FKc7odaqk7c11gi9bcwy6ew0sga (auto-gerada pelo Hibernate quando a FK
--     nomeada ainda apontava para users)
--
-- A migração é condicional: se a FK duplicada não existir (ex.: ambientes
-- onde ddl-auto nunca a criou), vira no-op e passa sem alterar nada.

SET @fk_exists = (
    SELECT COUNT(*)
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'form_templates'
       AND CONSTRAINT_NAME = 'FKc7odaqk7c11gi9bcwy6ew0sga'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @sql = IF(@fk_exists = 1,
    'ALTER TABLE form_templates DROP FOREIGN KEY FKc7odaqk7c11gi9bcwy6ew0sga',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
