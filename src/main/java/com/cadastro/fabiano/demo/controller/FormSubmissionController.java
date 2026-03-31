package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.service.FormSubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/form-submissions")
public class FormSubmissionController {

    private final FormSubmissionService submissionService;

    public FormSubmissionController(FormSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    // =========================
    // ENVIAR RESPOSTA (PÚBLICO)
    // =========================
    @PostMapping
    public FormSubmissionResponse submitForm(@RequestBody CreateFormSubmissionRequest request) {
        return submissionService.submitForm(request);
    }

    // =========================
    // LISTAR POR TEMPLATE ID
    // =========================
    @GetMapping("/template/{templateId}")
    public List<FormSubmissionResponse> getByTemplate(@PathVariable Long templateId) {
        return submissionService.getSubmissionsByTemplate(templateId);
    }

    // =========================
    // 🔥 LISTAR POR SLUG (IDEAL)
    // =========================
    @GetMapping("/slug/{slug}")
    public List<FormSubmissionResponse> getBySlug(@PathVariable String slug) {
        return submissionService.getSubmissionsBySlug(slug);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        submissionService.deleteSubmission(id);
        return ResponseEntity.noContent().build();
    }
}