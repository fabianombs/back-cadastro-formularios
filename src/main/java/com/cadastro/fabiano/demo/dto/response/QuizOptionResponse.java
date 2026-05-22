package com.cadastro.fabiano.demo.dto.response;

public record QuizOptionResponse(
    Long id,
    String optionText,
    int orderIndex,
    // isCorrect só é enviado após o jogador responder ou na edição admin
    Boolean correct
) {}
