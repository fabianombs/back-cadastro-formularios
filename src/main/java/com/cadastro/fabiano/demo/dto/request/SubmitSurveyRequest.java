package com.cadastro.fabiano.demo.dto.request;

public record SubmitSurveyRequest(
        Integer score,
        String comment,
        String respondentRef,
        String sourceTemplateSlug
) {}
