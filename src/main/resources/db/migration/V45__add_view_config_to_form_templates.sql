-- Adiciona configuração de link de visualização para clientes
-- view_token: UUID único por formulário, gerado pelo serviço ao criar/atualizar
-- view_allow_*: toggles que o admin controla para definir o que o cliente pode ver
ALTER TABLE form_templates
    ADD COLUMN view_token              VARCHAR(255) NULL UNIQUE,
    ADD COLUMN view_allow_export       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN view_show_submissions   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN view_show_attendance    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN view_show_appointments  BOOLEAN NOT NULL DEFAULT TRUE;

-- Gera token para formulários já existentes (não quebra nada em prod)
UPDATE form_templates
SET view_token = UUID()
WHERE view_token IS NULL;
