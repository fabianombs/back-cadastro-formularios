package com.cadastro.fabiano.demo.dto.request;

public record QuizOptionRequest(
    String optionText,
    boolean correct,
    int orderIndex
) {}
