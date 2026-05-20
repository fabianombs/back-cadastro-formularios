package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalDateTime;

public record AttendanceCompanionResponse(
        Long id,
        Long recordId,
        String name,
        String phone,
        boolean attended,
        LocalDateTime attendedAt,
        LocalDateTime createdAt
) {}
