package com.cadastro.fabiano.demo.dto.request;

import java.util.Map;

public record CreateFormSubmissionRequest(
        Long templateId,
        Map<String, String> values // chave = label do campo, valor = valor preenchido
) {}
