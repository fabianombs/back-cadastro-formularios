ALTER TABLE form_templates
    ADD COLUMN has_schedule      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN schedule_start_time TIME        NULL,
    ADD COLUMN schedule_end_time   TIME        NULL,
    ADD COLUMN slot_duration_minutes INT       NULL,
    ADD COLUMN max_days_ahead    INT          NULL DEFAULT 30;
