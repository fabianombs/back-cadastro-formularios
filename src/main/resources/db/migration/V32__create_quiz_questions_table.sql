-- Perguntas do quiz (N por quiz)
CREATE TABLE quiz_questions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id      BIGINT NOT NULL,
    question     VARCHAR(1000) NOT NULL,
    image_url    VARCHAR(500) NULL,
    order_index  INT NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id) REFERENCES quiz_configs(id) ON DELETE CASCADE
);
