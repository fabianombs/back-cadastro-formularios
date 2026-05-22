-- ══════════════════════════════════════════════════════════════════
-- V36: Quiz independente de FormTemplate
--   1. Adiciona name e slug em quiz_configs (identidade própria)
--   2. Adiciona quiz_config_id em form_templates (link opcional)
--   3. Migra dados existentes (quiz → template FK invertida)
--   4. Remove template_id de quiz_configs
--   5. Troca template_id por quiz_config_id em quiz_sessions
-- ══════════════════════════════════════════════════════════════════

-- 1. Adicionar campos de identidade ao quiz
ALTER TABLE quiz_configs ADD COLUMN name        VARCHAR(255) NOT NULL DEFAULT 'Quiz';
ALTER TABLE quiz_configs ADD COLUMN slug        VARCHAR(255);
ALTER TABLE quiz_configs ADD COLUMN updated_at  TIMESTAMP;

-- 2. Adicionar FK opcional de form_template → quiz_config
ALTER TABLE form_templates ADD COLUMN quiz_config_id BIGINT;
ALTER TABLE form_templates
    ADD CONSTRAINT fk_form_template_quiz_config
    FOREIGN KEY (quiz_config_id) REFERENCES quiz_configs(id) ON DELETE SET NULL;

-- 3. Migrar dados: preencher name/slug dos quizzes e quiz_config_id nos templates
UPDATE quiz_configs qc
SET name = (SELECT ft.name FROM form_templates ft WHERE ft.id = qc.template_id),
    slug = (SELECT ft.slug FROM form_templates ft WHERE ft.id = qc.template_id)
WHERE qc.template_id IS NOT NULL;

UPDATE form_templates ft
SET quiz_config_id = (SELECT qc.id FROM quiz_configs qc WHERE qc.template_id = ft.id)
WHERE EXISTS (SELECT 1 FROM quiz_configs qc WHERE qc.template_id = ft.id);

-- 4. Remover template_id de quiz_configs

-- 4a. Remover FK (criado em V31 com nome fk_quiz_template)
ALTER TABLE quiz_configs DROP FOREIGN KEY fk_quiz_template;

-- 4b. Remover índice único do template_id
--     V31 declarou UNIQUE inline → MySQL nomeia o índice igual à coluna: "template_id"
DROP INDEX template_id ON quiz_configs;

-- 4c. Remover coluna
ALTER TABLE quiz_configs DROP COLUMN template_id;

-- 4d. Garantir unicidade de slug
ALTER TABLE quiz_configs ADD CONSTRAINT uq_quiz_config_slug UNIQUE (slug);

-- 5. Migrar quiz_sessions: substituir template_id por quiz_config_id
ALTER TABLE quiz_sessions ADD COLUMN quiz_config_id BIGINT;

-- Preencher quiz_config_id a partir do template_id existente nas sessões
UPDATE quiz_sessions qs
SET quiz_config_id = (
    SELECT ft.quiz_config_id
    FROM form_templates ft
    WHERE ft.id = qs.template_id
)
WHERE qs.template_id IS NOT NULL;

-- Adicionar FK para o novo campo
ALTER TABLE quiz_sessions
    ADD CONSTRAINT fk_quiz_session_quiz_config
    FOREIGN KEY (quiz_config_id) REFERENCES quiz_configs(id);

-- Remover FK antiga de quiz_sessions → form_templates (criada em V34)
ALTER TABLE quiz_sessions DROP FOREIGN KEY fk_session_template;

-- Remover coluna antiga
ALTER TABLE quiz_sessions DROP COLUMN template_id;
