package com.cadastro.fabiano.demo.dto.response;

public record TemplateAppearanceResponse(
        String backgroundColor,
        String backgroundGradient,
        String backgroundImageUrl,
        String headerImageUrl,
        String footerImageUrl,
        String primaryColor,
        String formTextColor,
        String fieldBackgroundColor,
        String fieldTextColor,
        String cardBackgroundColor,
        String cardBorderColor,
        String fontFamily,
        String titleFontSize,
        String labelFontSize,
        String buttonFontSize
) {}
