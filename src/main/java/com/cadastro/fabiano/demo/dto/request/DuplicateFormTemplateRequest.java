package com.cadastro.fabiano.demo.dto.request;

/**
 * Dados para duplicar um template: cliente que recebe a cópia e o nome dela.
 * O link público (slug) é gerado a partir do nome, como na criação.
 */
public record DuplicateFormTemplateRequest(
        Long clientId,
        String name
) {}
