package com.cadastro.fabiano.demo.dto.response;

import com.cadastro.fabiano.demo.entity.EquipmentCatalog;

public record EquipmentCatalogResponse(
        Long id,
        Long templateId,
        String name,
        String columnKey,
        String sourceColumn,
        boolean stockControl,
        boolean visible,
        int optionsCount
) {
    public static EquipmentCatalogResponse from(EquipmentCatalog c, int optionsCount) {
        return new EquipmentCatalogResponse(
                c.getId(),
                c.getFormTemplate().getId(),
                c.getName(),
                c.getColumnKey(),
                c.getSourceColumn(),
                c.isStockControl(),
                c.isVisible(),
                optionsCount
        );
    }
}
