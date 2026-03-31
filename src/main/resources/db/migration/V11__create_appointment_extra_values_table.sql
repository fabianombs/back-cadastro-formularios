CREATE TABLE appointment_extra_values (
    appointment_id  BIGINT          NOT NULL,
    field_label     VARCHAR(255)    NOT NULL,
    field_value     TEXT            NULL,

    PRIMARY KEY (appointment_id, field_label),
    CONSTRAINT fk_appt_extra FOREIGN KEY (appointment_id)
        REFERENCES appointments(id) ON DELETE CASCADE
);
