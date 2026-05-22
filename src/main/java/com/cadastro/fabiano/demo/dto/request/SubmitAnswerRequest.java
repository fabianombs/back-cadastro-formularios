package com.cadastro.fabiano.demo.dto.request;

public record SubmitAnswerRequest(
    Long questionId,
    Long optionId,   // null se tempo esgotou
    long timeTakenMs
) {}
