package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateSurveyRequest;
import com.cadastro.fabiano.demo.dto.request.SubmitSurveyRequest;
import com.cadastro.fabiano.demo.dto.response.SurveyConfigResponse;
import com.cadastro.fabiano.demo.dto.response.SurveyReportResponse;
import com.cadastro.fabiano.demo.service.SurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/surveys")
@Tag(name = "Survey", description = "Pesquisas de satisfação")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Listar todas as pesquisas")
    public ResponseEntity<List<SurveyConfigResponse>> listAll() {
        return ResponseEntity.ok(surveyService.listAll());
    }

    @PostMapping
    @Operation(summary = "Criar nova pesquisa de satisfação")
    public ResponseEntity<SurveyConfigResponse> create(@RequestBody CreateSurveyRequest request) {
        return ResponseEntity.ok(surveyService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pesquisa pelo ID")
    public ResponseEntity<SurveyConfigResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pesquisa")
    public ResponseEntity<SurveyConfigResponse> update(
            @PathVariable Long id,
            @RequestBody CreateSurveyRequest request) {
        return ResponseEntity.ok(surveyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir pesquisa")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        surveyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Ativar/desativar pesquisa")
    public ResponseEntity<SurveyConfigResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.toggleActive(id));
    }

    @GetMapping("/{id}/report")
    @Operation(summary = "Relatório completo da pesquisa (admin)")
    public ResponseEntity<SurveyReportResponse> getReport(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getReport(id));
    }

    // ── Público ───────────────────────────────────────────────────────────────

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Buscar pesquisa pública pelo slug")
    public ResponseEntity<SurveyConfigResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(surveyService.getBySlug(slug));
    }

    @PostMapping("/slug/{slug}/responses")
    @Operation(summary = "Submeter resposta da pesquisa")
    public ResponseEntity<Void> submitResponse(
            @PathVariable String slug,
            @RequestBody SubmitSurveyRequest request) {
        surveyService.submitResponse(slug, request);
        return ResponseEntity.noContent().build();
    }
}
