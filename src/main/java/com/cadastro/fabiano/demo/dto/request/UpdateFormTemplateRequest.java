package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record UpdateFormTemplateRequest(
        String name,
        List<UpdateFormFieldRequest> fields,
        TemplateAppearanceRequest appearance,
        boolean lgpdEnabled,
        String lgpdText,
        // Toggles do link de visualização do cliente (null = não alterar)
        Boolean viewAllowExport,
        Boolean viewShowSubmissions,
        Boolean viewShowAttendance,
        Boolean viewShowAppointments,
        // Slug personalizado do link do cliente (null = não alterar, ex: "coca-cola")
        String viewSlug
) {
}
