package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/form-templates")
public class FormTemplateController {

    private final FormTemplateService templateService;

    public FormTemplateController(FormTemplateService templateService) {
        this.templateService = templateService;
    }

    // Cria template e associa a um cliente (ADMIN)
    @PostMapping("/create/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FormTemplateResponse> createTemplate(
            @PathVariable Long clientId,
            @RequestBody CreateFormTemplateRequest request) {
        return ResponseEntity.ok(templateService.createTemplate(request, clientId));
    }

    // Lista templates do usuário logado
    @GetMapping("/my-templates")
    public ResponseEntity<List<FormTemplateResponse>> getMyTemplates(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(templateService.findTemplatesByUsername(username));
    }

    // Lista todos os templates (ADMIN)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FormTemplateResponse>> getAllTemplates() {
        return ResponseEntity.ok(templateService.findAllTemplates());
    }
}