package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record FormFieldResponse(
        Long id,
        String label,
        String type,
        boolean required,
        String fieldColor,
        /** 2 = largura total, 1 = meia largura */
        int colSpan,
        /** Opções disponíveis para campos do tipo "select" */
        List<String> options
) {}
