package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SurveyReportResponse(
        Long surveyId,
        String surveyName,
        Long totalResponses,
        Double averageScore,
        // Chave: label do score ("Muito Satisfeito", ...), Valor: contagem
        Map<String, Long> scoreDistribution,
        List<SurveyResponseItem> responses
) {
    public record SurveyResponseItem(
            Long id,
            Integer score,
            String scoreLabel,
            String comment,
            String respondentRef,
            String sourceTemplateSlug,
            LocalDateTime createdAt
    ) {}
}
