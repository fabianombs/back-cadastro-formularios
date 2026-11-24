package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record QuizQuestionResponse(
    Long id,
    String question,
    String imageUrl,
    int orderIndex,
    List<QuizOptionResponse> options,
    // Só para o editor; no link público vai em AnswerResultResponse, depois da resposta
    String explanation
) {}
