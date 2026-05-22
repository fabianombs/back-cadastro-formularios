package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record QuizConfigRequest(
    String name,       // nome do quiz (obrigatório ao criar, opcional ao atualizar)
    String slug,       // slug do quiz (opcional; gerado automaticamente se ausente)
    int timePerQuestion,
    int pointsPerQuestion,
    List<QuizQuestionRequest> questions,
    // Aparência visual — todos opcionais (null = usar padrão)
    String backgroundColor,
    String backgroundGradient,
    String backgroundImageUrl,
    String primaryColor,
    String textColor,
    // Cor dos cards de resposta — null usa o padrão glassmorphism
    String cardColor
) {}
