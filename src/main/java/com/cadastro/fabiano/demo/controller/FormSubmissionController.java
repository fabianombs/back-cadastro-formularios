package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.service.FormSubmissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/form-submissions")
public class FormSubmissionController {

    private final FormSubmissionService submissionService;

    public FormSubmissionController(FormSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public FormSubmissionResponse submitForm(@RequestBody CreateFormSubmissionRequest request) {
        return submissionService.submitForm(request);
    }

    @GetMapping("/template/{templateId}")
    public Page<FormSubmissionResponse> getByTemplate(
            @PathVariable Long templateId,
            Pageable pageable) {
        return submissionService.getSubmissionsByTemplate(templateId, pageable);
    }

    @GetMapping("/slug/{slug}")
    public Page<FormSubmissionResponse> getBySlug(
            @PathVariable String slug,
            Pageable pageable) {
        return submissionService.getSubmissionsBySlug(slug, pageable);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        submissionService.deleteSubmission(id);
        return ResponseEntity.noContent().build();
    }
}