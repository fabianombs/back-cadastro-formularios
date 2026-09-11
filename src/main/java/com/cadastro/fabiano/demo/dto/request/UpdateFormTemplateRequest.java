package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record UpdateFormTemplateRequest(
        String name,
        List<UpdateFormFieldRequest> fields,
        TemplateAppearanceRequest appearance,
        boolean lgpdEnabled,
        String lgpdText,
        boolean thankYouEnabled,
        String thankYouTitle,
        String thankYouSubtitle,
        String thankYouParagraph,
        // Toggles do link de visualização do cliente (null = não alterar)
        Boolean viewAllowExport,
        Boolean viewShowSubmissions,
        Boolean viewShowAttendance,
        Boolean viewShowAppointments,
        // Slug personalizado do link do cliente (null = não alterar, ex: "coca-cola")
        String viewSlug,
        // ID da pesquisa de satisfação a vincular (null = não alterar, 0 = desvincular)
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
