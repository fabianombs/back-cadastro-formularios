package com.cadastro.fabiano.demo.dto.response;

import java.time.LocalDateTime;

public record SurveyConfigResponse(
        Long id,
        String name,
        String slug,
        String companyName,
        String companyLogoUrl,
        String welcomeTitle,
        String questionText,
        Boolean showComment,
        String thankYouMsg,
        Boolean active,
        String publicLink,
        String reportLink,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // Aparência visual
        String backgroundColor,
        String backgroundGradient,
        String backgroundImageUrl,
        String primaryColor,
        String textColor,
        String cardColor,
        String buttonColor,
        String buttonTextColor,
        String logoBorderRadius,
        // Posições livres
        Double logoPosX,
        Double logoPosY,
        Integer logoWidth,
        Double cardPosX,
        Double cardPosY,
        // Visibilidade do logo por tela
        Boolean showLogoWelcome,
        Boolean showLogoRating,
        Boolean showLogoThankyou,
        // Ícones customizados por score
        String score5ImageUrl,
        String score4ImageUrl,
        String score3ImageUrl,
        String score2ImageUrl,
        String score1ImageUrl,
        // Labels editáveis por score
        String score5Label,
        String score4Label,
        String score3Label,
        String score2Label,
        String score1Label,
        // Subtítulos e botões editáveis
        String welcomeSubtitle,
        String thankyouSubtitle,
        String welcomeBtnText,
        String ratingBtnText
) {}
