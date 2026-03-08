package com.cadastro.fabiano.demo.dto.request;

import java.util.Map;

public record FormSubmissionRequest(
        Long formTemplateId,
        Map<String, String> data
) {}