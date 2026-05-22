package com.cadastro.fabiano.demo.dto.response;

public record AnswerResultResponse(
    boolean correct,
    Long correctOptionId,
    int pointsEarned,
    int totalScore
) {}
