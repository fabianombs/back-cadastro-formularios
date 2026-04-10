package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.service.FormSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/form-submissions")
@Tag(name = "Submissões de Formulário", description = "Recebimento e consulta de submissões de formulários públicos")
public class FormSubmissionController {

    private final FormSubmissionService submissionService;

    public FormSubmissionController(FormSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Enviar resposta de formulário", description = "Endpoint público — salva as respostas dos campos de um formulário")
    @ApiResponse(responseCode = "200", description = "Submissão registrada com sucesso")
    public FormSubmissionResponse submitForm(@RequestBody CreateFormSubmissionRequest request) {
        return submissionService.submitForm(request);
    }

    @GetMapping("/template/{templateId}")
    @Operation(summary = "Listar submissões por template ID")
    public Page<FormSubmissionResponse> getByTemplate(
            @PathVariable Long templateId,
            Pageable pageable) {
        return submissionService.getSubmissionsByTemplate(templateId, pageable);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Listar submissões por slug do template")
    public Page<FormSubmissionResponse> getBySlug(
            @PathVariable String slug,
            Pageable pageable) {
        return submissionService.getSubmissionsBySlug(slug, pageable);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    @Operation(summary = "Excluir submissão")
    @ApiResponse(responseCode = "204", description = "Submissão excluída")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        submissionService.deleteSubmission(id);
        return ResponseEntity.noContent().build();
    }
}