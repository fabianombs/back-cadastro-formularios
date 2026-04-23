package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record UpdateFormTemplateRequest(
        String name,
        List<UpdateFormFieldRequest> fields,
        TemplateAppearanceRequest appearance,
        boolean lgpdEnabled,
        String lgpdText
) {
}
