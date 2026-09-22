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
        boolean thankYouEnabled,
        String thankYouTitle,
        String thankYouSubtitle,
        String thankYouParagraph,
        // Quiz integrado: presentes apenas quando existe um QuizConfig ativo para o template
        boolean hasQuiz,
        Long quizId,
        String quizLink,
        String rankingLink,
        // Configurações do link de visualização do cliente
        String viewToken,
        boolean viewAllowExport,
        boolean viewShowSubmissions,
        boolean viewShowAttendance,
        boolean viewShowAppointments,
        boolean viewAllowAttendanceCheck,
        boolean viewAllowAddGuest,
        // Visibilidade das colunas internas da lista de presenca
        boolean attendanceShowCompanions,
        boolean attendanceShowPresence,
        boolean attendanceShowNotes,
        boolean attendanceShowMarkedAt,
        boolean attendanceShowNumber,
        // Pesquisa de satisfação vinculada
        boolean hasSurvey,
        Long surveyId,
        String surveySlug,
        String surveyPublicLink,
        String attendanceFontScale,
        String thankYouBgColor,
        String thankYouBgGradient,
        String thankYouBgImageUrl,
        String thankYouBgImageMobileUrl,
        String thankYouBgImageTabletUrl,
        String thankYouIconColor,
        String thankYouTitleColor,
        String thankYouTextColor,
        String thankYouFontFamily,
        String thankYouTitleFontSize
) {}
