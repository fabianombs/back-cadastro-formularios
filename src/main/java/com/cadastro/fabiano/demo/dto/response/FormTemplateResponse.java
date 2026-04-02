package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record FormTemplateResponse(
        Long id,
        String name,
        String slug,
        String clientName,
        List<FormFieldResponse> fields,
        boolean hasSchedule,
        boolean hasAttendance,
        ScheduleConfigResponse scheduleConfig,
        TemplateAppearanceResponse appearance
) {}
