CREATE TABLE IF NOT EXISTS form_submission_values (
    submission_id BIGINT NOT NULL,
    field_label   VARCHAR(150) NOT NULL,
    field_value   TEXT,
    CONSTRAINT pk_form_submission_values PRIMARY KEY (submission_id, field_label),
    CONSTRAINT fk_submission_values FOREIGN KEY (submission_id)
        REFERENCES form_submissions(id)
        ON DELETE CASCADE
);
