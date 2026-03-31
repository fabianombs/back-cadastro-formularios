package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record AppointmentPageResponse(
        List<AppointmentResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        long confirmedCount,
        long cancelledCount
) {}
