package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailableSlotsResponse(
        LocalDate date,
        List<SlotInfo> slots
) {
    public record SlotInfo(
            LocalTime time,
            boolean available
    ) {}
}
