CREATE TABLE appointments (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    form_template_id    BIGINT          NOT NULL,
    slot_date           DATE            NOT NULL,
    slot_time           TIME            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'AGENDADO',
    booked_by_name      VARCHAR(255)    NULL,
    booked_by_contact   VARCHAR(255)    NULL,
    cancelled_by        VARCHAR(255)    NULL,
    cancelled_at        DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_appt_template FOREIGN KEY (form_template_id)
        REFERENCES form_templates(id) ON DELETE CASCADE
);
