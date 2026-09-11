-- Imagens de fundo específicas por dispositivo (mobile/tablet), opcionais.
-- Sem elas, o front-end continua usando a imagem de fundo "web" (background_image_url)
-- em qualquer tela, exatamente como hoje — nenhum template existente muda de comportamento.

ALTER TABLE form_templates
    ADD COLUMN background_image_mobile_url VARCHAR(1000),
    ADD COLUMN background_image_tablet_url VARCHAR(1000);

ALTER TABLE quiz_configs
    ADD COLUMN background_image_mobile_url VARCHAR(255),
    ADD COLUMN background_image_tablet_url VARCHAR(255);

ALTER TABLE survey_configs
    ADD COLUMN background_image_mobile_url TEXT,
    ADD COLUMN background_image_tablet_url TEXT;
