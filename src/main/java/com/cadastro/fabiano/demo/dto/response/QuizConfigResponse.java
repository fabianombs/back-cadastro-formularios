package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record QuizConfigResponse(
    Long id,
    String name,
    String slug,
    int timePerQuestion,
    int pointsPerQuestion,
    boolean active,
    int totalQuestions,
    List<QuizQuestionResponse> questions,
    // Links públicos gerados a partir do slug
    String quizLink,
    String rankingLink,
    // Aparência visual do quiz público
    String backgroundColor,
    String backgroundGradient,
    String backgroundImageUrl,
    String primaryColor,
    String textColor,
    String cardColor
) {}
