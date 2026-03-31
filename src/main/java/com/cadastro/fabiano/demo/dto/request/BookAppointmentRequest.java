package com.cadastro.fabiano.demo.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record BookAppointmentRequest(
        Long templateId,
        LocalDate slotDate,
        LocalTime slotTime,
        String bookedByName,
        String bookedByContact,
        Map<String, String> extraValues
) {}
