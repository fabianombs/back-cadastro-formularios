-- Adiciona controle de presença individual para cada acompanhante.
ALTER TABLE attendance_companions
    ADD COLUMN attended     BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN attended_at  DATETIME NULL;
