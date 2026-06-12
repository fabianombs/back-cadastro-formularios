package com.cadastro.fabiano.demo.dto.response;

/** Resultado da seleção de um equipamento em uma linha da lista de presença. */
public record EquipmentSelectionResponse(
        Long recordId,
        String columnKey,
        String label
) {}
