-- Tela de agradecimento final (opcional) — título, subtítulo e parágrafo editáveis,
-- exibida ao final do fluxo. Sem preenchimento/habilitação, comportamento de sempre
-- (nenhum template existente muda de aparência).

ALTER TABLE form_templates
    ADD COLUMN thank_you_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN thank_you_title     VARCHAR(255),
    ADD COLUMN thank_you_subtitle  VARCHAR(255),
    ADD COLUMN thank_you_paragraph TEXT;

ALTER TABLE quiz_configs
    ADD COLUMN thank_you_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN thank_you_title     VARCHAR(255),
    ADD COLUMN thank_you_subtitle  VARCHAR(255),
    ADD COLUMN thank_you_paragraph TEXT;

-- Pesquisa já tem título/subtítulo (thank_you_msg / thankyou_subtitle) desde sempre;
-- só falta o parágrafo extra, sempre exibido junto (sem toggle, já que a tela final
-- da pesquisa já é uma etapa obrigatória do fluxo).
ALTER TABLE survey_configs
    ADD COLUMN thank_you_paragraph TEXT;
