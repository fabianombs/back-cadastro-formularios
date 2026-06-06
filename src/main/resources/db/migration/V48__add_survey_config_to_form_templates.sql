ALTER TABLE form_templates ADD COLUMN survey_config_id BIGINT;

ALTER TABLE form_templates
    ADD CONSTRAINT fk_form_template_survey
    FOREIGN KEY (survey_config_id) REFERENCES survey_configs(id) ON DELETE SET NULL;
