ALTER TABLE form_templates
    ADD COLUMN lgpd_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN lgpd_text    TEXT;
