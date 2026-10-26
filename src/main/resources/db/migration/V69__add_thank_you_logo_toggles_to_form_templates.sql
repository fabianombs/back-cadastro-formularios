-- Opcao de exibir (ou nao) o logo do topo e do rodape na tela de agradecimento.
-- DEFAULT TRUE: nenhum template existente muda de comportamento.

ALTER TABLE form_templates
    ADD COLUMN show_header_logo_on_thank_you BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN show_footer_logo_on_thank_you BOOLEAN NOT NULL DEFAULT TRUE;
