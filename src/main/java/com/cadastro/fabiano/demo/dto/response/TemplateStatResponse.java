package com.cadastro.fabiano.demo.dto.response;

public record TemplateStatResponse(
        Long id,
        String name,
        String slug,
        String clientName,
        boolean hasSchedule,
        long submissionCount,
        long appointmentTotal,
        long appointmentConfirmed,
        long appointmentCancelled,
        long attendanceTotal,
        long attendancePresent
) {}
