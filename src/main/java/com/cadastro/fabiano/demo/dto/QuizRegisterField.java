package com.cadastro.fabiano.demo.dto;

/**
 * Campo do cadastro do quiz. showInRanking = compoe o nome exibido no ranking publico;
 * os demais campos so aparecem para a equipe.
 */
public record QuizRegisterField(
    String id,
    String label,
    String type,
    boolean required,
    boolean showInRanking
) {}
