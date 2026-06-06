CREATE TABLE survey_responses (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    survey_id             BIGINT NOT NULL,
    score                 TINYINT NOT NULL COMMENT '1=Muito Insatisfeito, 2=Insatisfeito, 3=Regular, 4=Satisfeito, 5=Muito Satisfeito',
    comment               TEXT,
    respondent_ref        VARCHAR(255) COMMENT 'Referência ao registro pai quando vinculado a outro template',
    source_template_slug  VARCHAR(255) COMMENT 'Slug do template que originou a pesquisa, se vinculado',
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_survey_response_config
        FOREIGN KEY (survey_id) REFERENCES survey_configs(id) ON DELETE CASCADE
);
