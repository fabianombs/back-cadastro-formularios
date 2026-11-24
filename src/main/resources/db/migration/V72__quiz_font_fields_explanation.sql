-- Fonte enviada por quiz, campos de cadastro configuraveis e explicacao da resposta.
-- Tudo opcional: NULL mantem o comportamento atual.
CREATE TABLE quiz_fonts (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    file_name  VARCHAR(120) NOT NULL,
    mime_type  VARCHAR(40)  NOT NULL,
    data       MEDIUMBLOB   NOT NULL,
    created_at DATETIME     NOT NULL
);

ALTER TABLE quiz_configs   ADD COLUMN font_url        VARCHAR(300);
ALTER TABLE quiz_configs   ADD COLUMN font_bold_url   VARCHAR(300);
ALTER TABLE quiz_configs   ADD COLUMN register_fields TEXT;
ALTER TABLE quiz_questions ADD COLUMN explanation     VARCHAR(1000);
ALTER TABLE quiz_sessions  ADD COLUMN register_data   TEXT;
