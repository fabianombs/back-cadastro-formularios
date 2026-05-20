package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record AttendancePageResponse(
        List<AttendanceRecordResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        long presentCount,
        long absentCount,
        // Total de acompanhantes de todos os convidados da lista
        long totalCompanions,
        // Acompanhantes dos convidados que efetivamente compareceram
        long presentCompanions
) {}
