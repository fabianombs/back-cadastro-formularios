CREATE TABLE form_templates (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                name VARCHAR(150) NOT NULL,
                                client_id BIGINT NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_form_template_client FOREIGN KEY (client_id)
                                    REFERENCES users(id)
                                    ON DELETE CASCADE
);