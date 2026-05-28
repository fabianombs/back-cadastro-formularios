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
    String cardColor,
    // Cor dos cards de cadastro/ready — null = usar padrão glassmorphism
    String registerCardColor,
    // Cor de fundo dos inputs de cadastro — null = usar padrão semitransparente
    String inputColor,
    // Cor de fundo dos cards do ranking/pódio — null = usar padrão glassmorphism
    String rankingCardColor,
    // Cor do botão principal — null = usar primaryColor como fallback
    String buttonColor,
    // Cor do texto dos botões — null = usar #fff como fallback
    String buttonTextColor,
    // Texto editável da tela "Tudo pronto!" — null = usar padrão no frontend
    String readyTitle,
    String readyMessage
) {}
