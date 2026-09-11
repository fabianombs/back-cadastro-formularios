package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record CreateFormTemplateRequest(
        String name,
        Long clientId,
        List<FormFieldRequest> fields,
        ScheduleConfigRequest scheduleConfig,
        TemplateAppearanceRequest appearance,
        boolean lgpdEnabled,
        String lgpdText,
        boolean thankYouEnabled,
        String thankYouTitle,
        String thankYouSubtitle,
        String thankYouParagraph,
        // Quiz a vincular imediatamente após criar o template (opcional)
        Long quizId,
        // Slug personalizado do link de visualização do cliente (ex: "coca-cola")
        String viewSlug,
        // Pesquisa de satisfação a vincular imediatamente após criar o template (opcional)
        Long surveyConfigId,
        // Aparência opcional e independente da tela de agradecimento final —
        // qualquer campo null cai no valor equivalente da aparência do formulário
        String thankYouBgColor,
        String thankYouBgGradient,
        String thankYouBgImageUrl,
        String thankYouBgImageMobileUrl,
        String thankYouBgImageTabletUrl,
        String thankYouIconColor,
        String thankYouTitleColor,
        String thankYouTextColor,
        String thankYouFontFamily,
        String thankYouTitleFontSize
) {
}
