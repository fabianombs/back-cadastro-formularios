package com.cadastro.fabiano.demo.dto.response;

import com.cadastro.fabiano.demo.entity.EquipmentOption;

public record EquipmentOptionResponse(
        Long id,
        String label,
        int totalQty,
        int usedCount,
        int available
) {
    public static EquipmentOptionResponse from(EquipmentOption o) {
        return new EquipmentOptionResponse(
                o.getId(),
                o.getLabel(),
                o.getTotalQty(),
                o.getUsedCount(),
                o.getAvailable()
        );
    }
}
