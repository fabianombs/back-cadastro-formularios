-- Permite ao cliente ADICIONAR convidado pela view pública (botão "+ Convidado").
-- Padrão FALSE: preserva o comportamento atual (botão visível somente para admin/cliente).
ALTER TABLE form_templates
    ADD COLUMN view_allow_add_guest BOOLEAN NOT NULL DEFAULT FALSE;
