package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalTime;

public record ScheduleConfigResponse(
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        int maxDaysAhead
) {}
