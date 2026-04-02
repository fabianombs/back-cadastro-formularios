package com.cadastro.fabiano.demo.dto.response;

public record FormFieldResponse(
        Long id,
        String label,
        String type,
        boolean required,
        String fieldColor,
        /** 2 = largura total, 1 = meia largura */
        int colSpan
) {}
