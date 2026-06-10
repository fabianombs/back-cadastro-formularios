-- Permite que o cliente MARQUE presença pela view pública (/view/:token), sem login.
-- Padrão FALSE: comportamento atual (somente leitura) preservado para todos os templates.
ALTER TABLE form_templates
    ADD COLUMN view_allow_attendance_check BOOLEAN NOT NULL DEFAULT FALSE;
