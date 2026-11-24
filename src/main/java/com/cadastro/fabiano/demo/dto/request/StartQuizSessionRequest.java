package com.cadastro.fabiano.demo.dto.request;

public record StartQuizSessionRequest(
    String playerName,
    String playerContact,
    // Respostas dos campos configuráveis: id do campo → valor
    java.util.Map<String, String> fields
) {}
