-- Acompanhantes vinculados a um convidado da lista de presença.
-- Cada convidado (attendance_record) pode ter N acompanhantes com nome e telefone.
CREATE TABLE attendance_companions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    record_id   BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(50)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_companion_record
        FOREIGN KEY (record_id) REFERENCES attendance_records(id) ON DELETE CASCADE
);
