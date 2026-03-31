package com.cadastro.fabiano.demo.dto.request;

import java.time.LocalTime;

public record ScheduleConfigRequest(
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        int maxDaysAhead
) {}
