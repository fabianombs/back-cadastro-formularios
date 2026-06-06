-- Posição do logo: percentual (0-100) relativo ao container público
ALTER TABLE survey_configs ADD COLUMN logo_pos_x  FLOAT DEFAULT 50  COMMENT 'Posição horizontal do logo em % (0=esquerda, 100=direita)';
ALTER TABLE survey_configs ADD COLUMN logo_pos_y  FLOAT DEFAULT 12  COMMENT 'Posição vertical do logo em % (0=topo, 100=base)';
ALTER TABLE survey_configs ADD COLUMN logo_width  INT   DEFAULT 120 COMMENT 'Largura do logo em px';

-- Posição do card principal: percentual relativo ao container público
ALTER TABLE survey_configs ADD COLUMN card_pos_x  FLOAT DEFAULT 50  COMMENT 'Posição horizontal do card em %';
ALTER TABLE survey_configs ADD COLUMN card_pos_y  FLOAT DEFAULT 55  COMMENT 'Posição vertical do card em %';
