package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalDateTime;

public record QuizSessionResponse(
    Long id,
    String playerName,
    String playerContact,
    int totalScore,
    int correctAnswers,
    int totalQuestions,
    boolean completed,
    LocalDateTime completedAt,
    // Posição no ranking (calculada no momento da consulta)
    Integer rankPosition
) {}
