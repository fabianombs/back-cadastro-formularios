package com.cadastro.fabiano.demo.dto.request;

public record FormFieldRequest(
        String label,
        String type,
        boolean required
) {
}
