-- Cria tabela de opções para campos do tipo "select"
CREATE TABLE IF NOT EXISTS form_field_options (
    form_field_id BIGINT NOT NULL,
    option_value  VARCHAR(255) NOT NULL,
    option_order  INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_field_options_field
        FOREIGN KEY (form_field_id)
        REFERENCES form_fields (id)
        ON DELETE CASCADE
);

