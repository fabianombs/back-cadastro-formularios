-- Aparência independente e opcional para a tela de agradecimento final
-- (Formulário e Quiz). Sem preenchimento, a tela continua usando a
-- aparência já configurada no template/quiz (comportamento atual preservado).

ALTER TABLE form_templates
    ADD COLUMN thank_you_bg_color          VARCHAR(20),
    ADD COLUMN thank_you_bg_gradient       VARCHAR(255),
    ADD COLUMN thank_you_bg_image_url      VARCHAR(500),
    ADD COLUMN thank_you_bg_image_mobile_url VARCHAR(500),
    ADD COLUMN thank_you_bg_image_tablet_url VARCHAR(500),
    ADD COLUMN thank_you_icon_color        VARCHAR(20),
    ADD COLUMN thank_you_title_color       VARCHAR(20),
    ADD COLUMN thank_you_text_color        VARCHAR(20),
    ADD COLUMN thank_you_font_family       VARCHAR(100),
    ADD COLUMN thank_you_title_font_size   VARCHAR(20);

ALTER TABLE quiz_configs
    ADD COLUMN thank_you_bg_color          VARCHAR(20),
    ADD COLUMN thank_you_bg_gradient       VARCHAR(255),
    ADD COLUMN thank_you_bg_image_url      VARCHAR(500),
    ADD COLUMN thank_you_bg_image_mobile_url VARCHAR(500),
    ADD COLUMN thank_you_bg_image_tablet_url VARCHAR(500),
    ADD COLUMN thank_you_icon_color        VARCHAR(20),
    ADD COLUMN thank_you_title_color       VARCHAR(20),
    ADD COLUMN thank_you_text_color        VARCHAR(20),
    ADD COLUMN thank_you_font_family       VARCHAR(100),
    ADD COLUMN thank_you_title_font_size   VARCHAR(20);
