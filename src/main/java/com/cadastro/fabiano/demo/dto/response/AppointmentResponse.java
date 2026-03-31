package com.cadastro.fabiano.demo.dto.response;

import com.cadastro.fabiano.demo.entity.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

public record AppointmentResponse(
        Long id,
        Long templateId,
        String templateName,
        LocalDate slotDate,
        LocalTime slotTime,
        AppointmentStatus status,
        String bookedByName,
        String bookedByContact,
        String cancelledBy,
        LocalDateTime cancelledAt,
        Map<String, String> extraValues,
        LocalDateTime createdAt
) {}
