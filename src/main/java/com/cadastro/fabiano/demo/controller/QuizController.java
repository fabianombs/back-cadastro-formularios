package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.*;
import com.cadastro.fabiano.demo.dto.response.*;
import com.cadastro.fabiano.demo.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quizzes")
@Tag(name = "Quiz", description = "Biblioteca de quizzes independentes e execução pública")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    // ── Admin: CRUD de quizzes independentes ──────────────────────────────────

    @GetMapping
    @Operation(summary = "Listar todos os quizzes da biblioteca")
    public ResponseEntity<List<QuizConfigResponse>> listAll() {
        return ResponseEntity.ok(quizService.listAll());
    }

    @PostMapping
    @Operation(summary = "Criar novo quiz standalone")
    public ResponseEntity<QuizConfigResponse> createQuiz(@RequestBody QuizConfigRequest request) {
        return ResponseEntity.ok(quizService.createQuiz(request));
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Buscar quiz pelo ID (admin, com respostas corretas)")
    public ResponseEntity<QuizConfigResponse> getQuizById(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.getQuizById(quizId));
    }

    @PutMapping("/{quizId}")
    @Operation(summary = "Atualizar quiz (nome, slug, configurações e questões)")
    public ResponseEntity<QuizConfigResponse> updateQuiz(
            @PathVariable Long quizId,
            @RequestBody QuizConfigRequest request) {
        return ResponseEntity.ok(quizService.updateQuiz(quizId, request));
    }

    @DeleteMapping("/{quizId}")
    @Operation(summary = "Excluir quiz (só permitido se não estiver vinculado a nenhum template)")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long quizId) {
        quizService.deleteQuiz(quizId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{quizId}/toggle")
    @Operation(summary = "Ativar ou desativar o quiz")
    public ResponseEntity<QuizConfigResponse> toggleActive(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.toggleActive(quizId));
    }

    // ── Admin: associação quiz ↔ template ─────────────────────────────────────

    @PostMapping("/{quizId}/assign/{templateId}")
    @Operation(summary = "Vincular quiz a um template")
    public ResponseEntity<Void> assignToTemplate(
            @PathVariable Long quizId,
            @PathVariable Long templateId) {
        quizService.assignQuizToTemplate(templateId, quizId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/unassign/{templateId}")
    @Operation(summary = "Desvincular quiz de um template")
    public ResponseEntity<Void> unassignFromTemplate(@PathVariable Long templateId) {
        quizService.unassignQuizFromTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: ranking e relatório ────────────────────────────────────────────

    @GetMapping("/{quizId}/report")
    @Operation(summary = "Relatório admin de todos os participantes")
    public ResponseEntity<RankingResponse> getAdminReport(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.getAdminReport(quizId));
    }

    @DeleteMapping("/{quizId}/ranking")
    @Operation(summary = "Zerar ranking do quiz")
    public ResponseEntity<Void> resetRanking(@PathVariable Long quizId) {
        quizService.resetRanking(quizId);
        return ResponseEntity.noContent().build();
    }

    // ── Público: acesso pelo slug ─────────────────────────────────────────────

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Buscar quiz público pelo slug (sem respostas corretas)")
    public ResponseEntity<QuizConfigResponse> getQuizPublic(@PathVariable String slug) {
        return ResponseEntity.ok(quizService.getQuizBySlug(slug));
    }

    @PostMapping("/slug/{slug}/sessions")
    @Operation(summary = "Iniciar sessão de quiz")
    public ResponseEntity<QuizSessionResponse> startSession(
            @PathVariable String slug,
            @RequestBody StartQuizSessionRequest request) {
        return ResponseEntity.ok(quizService.startSession(slug, request));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    @Operation(summary = "Submeter resposta de uma pergunta")
    public ResponseEntity<AnswerResultResponse> submitAnswer(
            @PathVariable Long sessionId,
            @RequestBody SubmitAnswerRequest request) {
        return ResponseEntity.ok(quizService.submitAnswer(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    @Operation(summary = "Finalizar sessão e obter posição no ranking")
    public ResponseEntity<QuizSessionResponse> completeSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(quizService.completeSession(sessionId));
    }

    @GetMapping("/slug/{slug}/ranking")
    @Operation(summary = "Ranking público top N (padrão top 10)")
    public ResponseEntity<RankingResponse> getRanking(
            @PathVariable String slug,
            @RequestParam(defaultValue = "10") int top) {
        return ResponseEntity.ok(quizService.getRanking(slug, top));
    }
}
