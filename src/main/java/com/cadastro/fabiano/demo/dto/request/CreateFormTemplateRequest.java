package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record CreateFormTemplateRequest(
        String name,
        Long clientId,
        List<FormFieldRequest> fields,
        ScheduleConfigRequest scheduleConfig,
        TemplateAppearanceRequest appearance,
        boolean lgpdEnabled,
        String lgpdText,
        // Quiz a vincular imediatamente após criar o template (opcional)
        Long quizId
) {
}
