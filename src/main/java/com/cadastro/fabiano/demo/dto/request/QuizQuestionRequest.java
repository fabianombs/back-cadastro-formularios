package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record QuizQuestionRequest(
    String question,
    String imageUrl,
    int orderIndex,
    List<QuizOptionRequest> options
) {}
