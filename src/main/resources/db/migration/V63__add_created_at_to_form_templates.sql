-- Data de criacao do formulario, pedida pelo cliente (Leo) para aparecer nos
-- cards do painel de formularios ("so de olhar ja da pra ter uma nocao").
-- Nao existia rastreamento de quando um FormTemplate foi criado.
--
-- IDEMPOTENTE seguindo o padrao de V61: em ambiente ja migrado um ALTER ADD
-- COLUMN simples quebraria com "Duplicate column name", entao a checagem e
-- feita no information_schema antes.
--
-- DEFAULT CURRENT_TIMESTAMP faz o MySQL preencher os formularios ja existentes
-- com a data da migration (nao a data real de criacao, que nunca foi
-- registrada) — mesma limitacao aceita em V2 (clients.created_at).
SET @existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'form_templates'
      AND column_name  = 'created_at'
);

SET @ddl := IF(@existe = 0,
    'ALTER TABLE form_templates ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
