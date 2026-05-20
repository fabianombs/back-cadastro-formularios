package com.cadastro.fabiano.demo.dto.request;

public record MarkAttendanceRequest(
        boolean attended,
        String notes,
        // Quantidade de acompanhantes informada ao marcar presença (null = mantém valor atual)
        Integer companionsCount
) {}
