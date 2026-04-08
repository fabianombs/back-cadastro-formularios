package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.ScheduleConfigRequest;
import com.cadastro.fabiano.demo.dto.request.UpdateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/form-templates")
public class FormTemplateController {

    private final FormTemplateService templateService;

    public FormTemplateController(FormTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/create/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<FormTemplateResponse> createTemplate(
            @PathVariable Long clientId,
            @RequestBody CreateFormTemplateRequest request) {

        return ResponseEntity.ok(
                templateService.createTemplate(request, clientId)
        );
    }

    @GetMapping("/my-templates")
    public ResponseEntity<Page<FormTemplateResponse>> getMyTemplates(
            Authentication authentication,
            Pageable pageable) {

        return ResponseEntity.ok(
                templateService.findTemplatesByUsername(authentication.getName(), pageable)
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<Page<FormTemplateResponse>> getAllTemplates(Pageable pageable) {

        return ResponseEntity.ok(
                templateService.findAllTemplates(pageable)
        );
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<FormTemplateResponse> getBySlug(@PathVariable String slug) {

        return ResponseEntity.ok(
                templateService.findBySlug(slug)
        );
    }

    @PatchMapping("/{id}/schedule-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<FormTemplateResponse> updateScheduleConfig(
            @PathVariable Long id,
            @RequestBody ScheduleConfigRequest request) {

        return ResponseEntity.ok(templateService.updateScheduleConfig(id, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FormTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @RequestBody UpdateFormTemplateRequest request) {

        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}