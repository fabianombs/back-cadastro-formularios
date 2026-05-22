-- Opções de resposta por pergunta
CREATE TABLE quiz_options (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id  BIGINT NOT NULL,
    option_text  VARCHAR(500) NOT NULL,
    is_correct   BOOLEAN NOT NULL DEFAULT FALSE,
    order_index  INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_option_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
);
