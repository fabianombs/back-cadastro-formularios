ALTER TABLE survey_configs ADD COLUMN score_5_label VARCHAR(100) DEFAULT 'Muito Satisfeito';
ALTER TABLE survey_configs ADD COLUMN score_4_label VARCHAR(100) DEFAULT 'Satisfeito';
ALTER TABLE survey_configs ADD COLUMN score_3_label VARCHAR(100) DEFAULT 'Regular';
ALTER TABLE survey_configs ADD COLUMN score_2_label VARCHAR(100) DEFAULT 'Insatisfeito';
ALTER TABLE survey_configs ADD COLUMN score_1_label VARCHAR(100) DEFAULT 'Muito Insatisfeito';
