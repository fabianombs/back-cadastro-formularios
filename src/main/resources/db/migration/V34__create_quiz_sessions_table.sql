-- Sessão de um jogador (uma tentativa de quiz)
CREATE TABLE quiz_sessions (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id      BIGINT NOT NULL,
    player_name      VARCHAR(255) NOT NULL,
    player_contact   VARCHAR(255) NOT NULL,
    total_score      INT NOT NULL DEFAULT 0,
    correct_answers  INT NOT NULL DEFAULT 0,
    total_questions  INT NOT NULL DEFAULT 0,
    completed        BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at     DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_template FOREIGN KEY (template_id) REFERENCES form_templates(id) ON DELETE CASCADE
);
