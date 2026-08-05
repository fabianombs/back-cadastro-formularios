package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AttendanceRecordResponse(
        Long id,
        Long templateId,
        Map<String, String> rowData,
        boolean attended,
        LocalDateTime attendedAt,
        String notes,
        // companionsCount mantido como cache para stats rápidas; lista completa em companions
        int companionsCount,
        List<AttendanceCompanionResponse> companions,
        Integer rowOrder,
        LocalDateTime createdAt,
        LocalDateTime filledAt
) {}
