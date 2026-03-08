package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record CreateFormTemplateRequest(
        String name,
        Long clientId,
        List<FormFieldRequest> fields
) {
}
