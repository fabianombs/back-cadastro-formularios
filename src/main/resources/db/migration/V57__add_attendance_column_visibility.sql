-- Visibilidade das colunas internas da lista de presenca (mostrar/ocultar no admin).
ALTER TABLE form_templates
    ADD COLUMN attendance_show_companions BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN attendance_show_presence   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN attendance_show_notes      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN attendance_show_marked_at  BOOLEAN NOT NULL DEFAULT TRUE;
