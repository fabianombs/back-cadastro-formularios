package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.service.FormSubmissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/form-submissions")
public class FormSubmissionController {

    private final FormSubmissionService submissionService;

    public FormSubmissionController(FormSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    // ==========================
    // Cliente envia preenchimento
    // ==========================
    @PostMapping
    public FormSubmissionResponse submitForm(@RequestBody CreateFormSubmissionRequest request) {
        return submissionService.submitForm(request);
    }

    // ==========================
    // Listar envios de um template
    // ==========================
    @GetMapping
    public List<FormSubmissionResponse> getSubmissionsByTemplate(@RequestParam Long templateId) {
        return submissionService.getSubmissionsByTemplate(templateId);
    }
}