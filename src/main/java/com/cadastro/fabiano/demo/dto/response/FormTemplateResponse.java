package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record FormTemplateResponse(
        Long id,
        String name,
        String slug,
        String clientName,
        String clientCompany,
        List<FormFieldResponse> fields,
        boolean hasSchedule,
        boolean hasAttendance,
        List<String> attendanceColumnOrder,
        ScheduleConfigResponse scheduleConfig,
        TemplateAppearanceResponse appearance,
        boolean lgpdEnabled,
        String lgpdText,
        // Quiz integrado: presentes apenas quando existe um QuizConfig ativo para o template
        boolean hasQuiz,
        Long quizId,
        String quizLink,
        String rankingLink
) {}
