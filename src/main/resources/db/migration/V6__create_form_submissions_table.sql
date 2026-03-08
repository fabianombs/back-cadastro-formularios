CREATE TABLE form_submissions (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                  form_template_id BIGINT NOT NULL,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_submission_template FOREIGN KEY (form_template_id)
                                      REFERENCES form_templates(id)
                                      ON DELETE CASCADE
);