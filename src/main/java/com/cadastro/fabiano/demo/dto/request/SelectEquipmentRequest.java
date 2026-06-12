package com.cadastro.fabiano.demo.dto.request;

/**
 * Seleção de um equipamento para uma linha da lista de presença.
 * label nulo/vazio = limpar a seleção (devolve a unidade ao estoque, se ligado).
 */
public record SelectEquipmentRequest(
        Long recordId,
        Long catalogId,
        String columnKey,
        String label
) {}
