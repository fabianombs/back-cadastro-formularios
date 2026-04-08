package com.cadastro.fabiano.demo.dto.request;

public record UpdateFormFieldRequest(
        String label,
        String type,
        boolean required,
        String fieldColor,
        /** 2 = largura total, 1 = meia largura. Padrão: 2 */
        Integer colSpan
) {
}
