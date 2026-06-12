package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

/**
 * Importacao do catalogo de equipamentos (2a planilha).
 * O frontend le o Excel, agrega os valores distintos da coluna escolhida
 * e envia apenas a lista de opcoes (label + quantidade).
 */
public record ImportEquipmentRequest(
        String name,
        String columnKey,
        String sourceColumn,
        boolean stockControl,
        boolean visible,
        List<OptionInput> options
) {
    public record OptionInput(String label, Integer quantity) {}
}
