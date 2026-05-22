package com.cadastro.fabiano.demo.dto.response;

public record TemplateStatResponse(
        Long id,
        String name,
        String slug,
        String clientName,
        boolean hasSchedule,
        int fieldCount,
        long submissionCount,
        long appointmentTotal,
        long appointmentConfirmed,
        long appointmentCancelled,
        long attendanceTotal,
        long attendancePresent,
        // Quiz integrado — presentes quando o template tem quiz ativo
        boolean hasQuiz,
        String quizLink,
        String rankingLink
) {}
