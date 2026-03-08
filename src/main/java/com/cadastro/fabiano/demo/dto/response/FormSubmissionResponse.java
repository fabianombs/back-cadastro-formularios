package com.cadastro.fabiano.demo.dto.response;

import java.util.Map;
import java.time.LocalDateTime;

public record FormSubmissionResponse(
        Long id,
        Long templateId,
        Map<String, String> values,
        LocalDateTime createdAt
) {}