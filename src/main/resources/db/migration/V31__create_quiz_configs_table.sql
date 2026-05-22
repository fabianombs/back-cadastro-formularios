-- Configuração principal do quiz vinculada a um template
CREATE TABLE quiz_configs (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id          BIGINT NOT NULL UNIQUE,
    time_per_question    INT NOT NULL DEFAULT 30,
    points_per_question  INT NOT NULL DEFAULT 1000,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quiz_template FOREIGN KEY (template_id) REFERENCES form_templates(id) ON DELETE CASCADE
);
