package com.cadastro.fabiano.demo.dto.request;

/**
 * Formulario de destino ao mover todos os cadastros de um formulario para outro
 * (Story 1.4). Origem e destino precisam ser do mesmo cliente.
 */
public record MoveSubmissionsRequest(
        Long targetId
) {}
