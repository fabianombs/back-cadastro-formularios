package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record AttendancePageResponse(
        List<AttendanceRecordResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        long presentCount,
        long absentCount
) {}
