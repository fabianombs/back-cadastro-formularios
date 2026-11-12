package com.cadastro.fabiano.demo.dto.response;

/** Resultado de "mover cadastros" (Story 1.4): quantos cadastros mudaram de formulario. */
public record MoveSubmissionsResponse(
        int moved
) {}
