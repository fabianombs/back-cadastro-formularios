package com.cadastro.fabiano.demo.dto.request;

import java.util.List;

public record UpdateFormFieldRequest(
        String label,
        String type,
        boolean required,
        String fieldColor,
        /** 2 = largura total, 1 = meia largura. Padrão: 2 */
        Integer colSpan,
        /** Opções para campos do tipo "select". Ignorado para outros tipos. */
        List<String> options
) {
}
