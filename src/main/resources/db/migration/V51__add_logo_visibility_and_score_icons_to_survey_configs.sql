-- Controle de visibilidade do logo em cada tela (independente)
ALTER TABLE survey_configs ADD COLUMN show_logo_welcome  BOOLEAN NOT NULL DEFAULT TRUE  COMMENT 'Exibir logo na tela de boas-vindas';
ALTER TABLE survey_configs ADD COLUMN show_logo_rating   BOOLEAN NOT NULL DEFAULT TRUE  COMMENT 'Exibir logo na tela de avaliação';
ALTER TABLE survey_configs ADD COLUMN show_logo_thankyou BOOLEAN NOT NULL DEFAULT TRUE  COMMENT 'Exibir logo na tela de agradecimento';

-- Imagens customizadas para cada nível de score (null = usar emoji padrão)
ALTER TABLE survey_configs ADD COLUMN score_5_image_url TEXT COMMENT 'Ícone customizado para Muito Satisfeito';
ALTER TABLE survey_configs ADD COLUMN score_4_image_url TEXT COMMENT 'Ícone customizado para Satisfeito';
ALTER TABLE survey_configs ADD COLUMN score_3_image_url TEXT COMMENT 'Ícone customizado para Regular';
ALTER TABLE survey_configs ADD COLUMN score_2_image_url TEXT COMMENT 'Ícone customizado para Insatisfeito';
ALTER TABLE survey_configs ADD COLUMN score_1_image_url TEXT COMMENT 'Ícone customizado para Muito Insatisfeito';
