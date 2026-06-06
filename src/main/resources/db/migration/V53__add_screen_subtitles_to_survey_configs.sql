-- Subtítulos editáveis de cada tela
ALTER TABLE survey_configs ADD COLUMN welcome_subtitle  VARCHAR(500) DEFAULT 'Sua opinião é muito importante para nós!';
ALTER TABLE survey_configs ADD COLUMN thankyou_subtitle VARCHAR(500) DEFAULT 'Avaliação registrada com sucesso.';
-- Texto do botão de cada tela
ALTER TABLE survey_configs ADD COLUMN welcome_btn_text  VARCHAR(100) DEFAULT 'Começar';
ALTER TABLE survey_configs ADD COLUMN rating_btn_text   VARCHAR(100) DEFAULT 'Enviar avaliação';
