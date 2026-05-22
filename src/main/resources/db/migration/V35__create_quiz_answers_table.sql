-- Resposta individual por pergunta em uma sessão
CREATE TABLE quiz_answers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    question_id     BIGINT NOT NULL,
    option_id       BIGINT NULL,
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    points_earned   INT NOT NULL DEFAULT 0,
    time_taken_ms   BIGINT NOT NULL DEFAULT 0,
    answered_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_answer_session  FOREIGN KEY (session_id)  REFERENCES quiz_sessions(id)  ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_option   FOREIGN KEY (option_id)   REFERENCES quiz_options(id)   ON DELETE SET NULL
);
