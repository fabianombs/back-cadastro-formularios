package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.ScheduleConfigRequest;
import com.cadastro.fabiano.demo.dto.request.UpdateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/form-templates")
@Tag(name = "Templates de Formulário", description = "Criação e gerenciamento de formulários dinâmicos")
public class FormTemplateController {

    private final FormTemplateService templateService;

    public FormTemplateController(FormTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/create/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    @Operation(summary = "Criar template", description = "Cria um novo formulário dinâmico para um cliente. Permite configurar agenda e aparência")
    @ApiResponse(responseCode = "200", description = "Template criado com sucesso")
    public ResponseEntity<FormTemplateResponse> createTemplate(
            @PathVariable Long clientId,
            @RequestBody CreateFormTemplateRequest request) {

        return ResponseEntity.ok(templateService.createTemplate(request, clientId));
    }

    @GetMapping("/my-templates")
    @Operation(summary = "Meus templates", description = "Retorna os templates do usuário autenticado (CLIENT)")
    public ResponseEntity<Page<FormTemplateResponse>> getMyTemplates(
            Authentication authentication,
            Pageable pageable) {

        return ResponseEntity.ok(templateService.findTemplatesByUsername(authentication.getName(), pageable));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    @Operation(summary = "Listar todos os templates", description = "Retorna todos os templates ativos (ADMIN/FUNCIONARIO)")
    public ResponseEntity<Page<FormTemplateResponse>> getAllTemplates(Pageable pageable) {
        return ResponseEntity.ok(templateService.findAllTemplates(pageable));
    }

    @GetMapping("/slug/{slug}")
    @SecurityRequirements
    @Operation(summary = "Buscar template por slug", description = "Endpoint público — usado pelo formulário público para carregar configuração e campos")
    @ApiResponse(responseCode = "200", description = "Template encontrado")
    @ApiResponse(responseCode = "400", description = "Slug não encontrado")
    public ResponseEntity<FormTemplateResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(templateService.findBySlug(slug));
    }

    @PatchMapping("/{id}/schedule-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    @Operation(summary = "Atualizar configuração de agenda", description = "Define ou atualiza os parâmetros de agendamento (horários, duração, capacidade, deduplicação)")
    public ResponseEntity<FormTemplateResponse> updateScheduleConfig(
            @PathVariable Long id,
            @RequestBody ScheduleConfigRequest request) {

        return ResponseEntity.ok(templateService.updateScheduleConfig(id, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar template (ADMIN)", description = "Substitui nome, campos e aparência do template. Limpa imagens órfãs do disco")
    public ResponseEntity<FormTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @RequestBody UpdateFormTemplateRequest request) {

        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    @Operation(summary = "Excluir template (soft delete)", description = "Marca o template como excluído e remove imagens órfãs do disco")
    @ApiResponse(responseCode = "204", description = "Template excluído")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
