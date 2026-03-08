CREATE TABLE form_fields (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                             label VARCHAR(150) NOT NULL,
                             type VARCHAR(50) NOT NULL,
                             required BOOLEAN DEFAULT FALSE,
                             form_template_id BIGINT NOT NULL,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_form_field_template FOREIGN KEY (form_template_id)
                                 REFERENCES form_templates(id)
                                 ON DELETE CASCADE
);