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
    String cardColor,
    // Cor dos cards de cadastro/ready — null usa o padrão glassmorphism
    String registerCardColor,
    // Cor de fundo dos inputs de cadastro — null usa o padrão semitransparente
    String inputColor,
    // Cor de fundo dos cards do ranking/pódio — null usa o padrão glassmorphism
    String rankingCardColor,
    // Cor do botão principal — null usa primaryColor como fallback
    String buttonColor,
    // Cor do texto dos botões — null usa #fff como fallback
    String buttonTextColor,
    // Texto editável da tela "Tudo pronto!" — null usa padrão do frontend
    String readyTitle,
    String readyMessage
) {}
