package com.cadastro.fabiano.demo.dto.request;

/**
 * Configuração visual/aparência de um template de formulário.
 * Todos os campos são opcionais (null = usar padrão).
 */
public record TemplateAppearanceRequest(
        String backgroundColor,
        String backgroundGradient,
        String backgroundImageUrl,
        String headerImageUrl,
        String footerImageUrl,
        String primaryColor,
        String formTextColor,
        String fieldBackgroundColor,
        String fieldTextColor,
        /** Cor de fundo dos cards, tabelas e área de filtros */
        String cardBackgroundColor,
        /** Cor da borda dos cards e tabelas */
        String cardBorderColor
) {}
