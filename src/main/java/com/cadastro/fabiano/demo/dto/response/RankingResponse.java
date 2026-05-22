package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record RankingResponse(
    String templateName,
    long totalParticipants,
    List<QuizSessionResponse> top
) {}
