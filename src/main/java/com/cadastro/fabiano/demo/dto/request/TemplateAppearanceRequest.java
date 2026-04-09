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
        String cardBorderColor,
        /** Família tipográfica (ex: Poppins) */
        String fontFamily,
        /** Tamanho da fonte do título (ex: 22px) */
        String titleFontSize,
        /** Tamanho da fonte dos labels (ex: 14px) */
        String labelFontSize,
        /** Tamanho da fonte do botão (ex: 15px) */
        String buttonFontSize
) {}
