package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

/**
 * Previa de "mover cadastros" (Story 1.4).
 *
 * @param sourceCount   cadastros que serao movidos
 * @param targetCount   cadastros que o destino tem hoje
 * @param missingLabels perguntas respondidas na origem que nao existem no destino
 *                      (as respostas continuam guardadas, mas nao viram coluna no destino)
 */
public record MoveSubmissionsPreviewResponse(
        long sourceCount,
        long targetCount,
        List<String> missingLabels
) {}
