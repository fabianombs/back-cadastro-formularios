package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record FormTemplateResponse(
        Long id,
        String name,
        List<FormFieldResponse> fields
) {}