-- Logo do cliente e cores de texto do cadastro e das respostas — null mantém o visual atual
ALTER TABLE quiz_configs ADD COLUMN logo_url            VARCHAR(500);
ALTER TABLE quiz_configs ADD COLUMN register_text_color VARCHAR(80);
ALTER TABLE quiz_configs ADD COLUMN option_text_color   VARCHAR(80);
ALTER TABLE quiz_configs ADD COLUMN question_card_color VARCHAR(80);
ALTER TABLE quiz_configs ADD COLUMN secondary_text_color VARCHAR(80);
-- Em quais telas a logo aparece — por padrão, em todas
ALTER TABLE quiz_configs ADD COLUMN show_logo_register BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quiz_configs ADD COLUMN show_logo_ready BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quiz_configs ADD COLUMN show_logo_question BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quiz_configs ADD COLUMN show_logo_result BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quiz_configs ADD COLUMN show_logo_ranking BOOLEAN NOT NULL DEFAULT TRUE;
