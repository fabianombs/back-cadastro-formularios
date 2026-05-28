package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.*;
import com.cadastro.fabiano.demo.dto.response.*;
import com.cadastro.fabiano.demo.entity.*;
import com.cadastro.fabiano.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock private QuizConfigRepository quizConfigRepository;
    @Mock private QuizSessionRepository sessionRepository;
    @Mock private QuizAnswerRepository answerRepository;
    @Mock private FormTemplateRepository templateRepository;

    @InjectMocks
    private QuizService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl",     "https://test.example.com");
        // frontendUrl é usado em toConfigResponse() para gerar quizLink e rankingLink
        ReflectionTestUtils.setField(service, "frontendUrl", "https://test.example.com");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private QuizConfig buildQuiz(Long id, String name, String slug) {
        List<QuizQuestion> questions = new ArrayList<>();
        QuizConfig q = QuizConfig.builder()
                .id(id).name(name).slug(slug)
                .timePerQuestion(30).pointsPerQuestion(1000)
                .active(true).questions(questions)
                .build();
        return q;
    }

    private QuizQuestion buildQuestion(Long id, QuizConfig quiz) {
        List<QuizOption> opts = new ArrayList<>();
        QuizQuestion q = QuizQuestion.builder()
                .id(id).quizConfig(quiz)
                .question("Pergunta " + id).imageUrl(null).orderIndex(0)
                .options(opts).build();
        return q;
    }

    private QuizOption buildOption(Long id, QuizQuestion q, boolean correct) {
        return QuizOption.builder()
                .id(id).question(q)
                .optionText("Opção " + id).correct(correct).orderIndex(0)
                .build();
    }

    private QuizConfigRequest buildRequest(String name) {
        List<QuizOptionRequest> opts = List.of(
                new QuizOptionRequest("A", true, 0),
                new QuizOptionRequest("B", false, 1)
        );
        List<QuizQuestionRequest> questions = List.of(
                new QuizQuestionRequest("P1?", null, 0, opts)
        );
        return new QuizConfigRequest(name, null, 30, 1000, questions,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAll: retorna lista de quizzes")
    void listAll_returnsAll() {
        QuizConfig q1 = buildQuiz(1L, "Quiz 1", "quiz-1");
        QuizConfig q2 = buildQuiz(2L, "Quiz 2", "quiz-2");
        when(quizConfigRepository.findAll()).thenReturn(List.of(q1, q2));

        List<QuizConfigResponse> result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Quiz 1");
        assertThat(result.get(1).slug()).isEqualTo("quiz-2");
    }

    // ── createQuiz ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createQuiz: cria com slug gerado do nome")
    void createQuiz_generatesSlug() {
        QuizConfigRequest req = buildRequest("Meu Quiz");
        QuizConfig saved = buildQuiz(1L, "Meu Quiz", "meu-quiz");

        when(quizConfigRepository.existsBySlug("meu-quiz")).thenReturn(false);
        when(quizConfigRepository.save(any())).thenReturn(saved);

        QuizConfigResponse result = service.createQuiz(req);

        assertThat(result.name()).isEqualTo("Meu Quiz");
        assertThat(result.slug()).isEqualTo("meu-quiz");
        verify(quizConfigRepository).save(any());
    }

    @Test
    @DisplayName("createQuiz: slug com sufixo numérico quando já existe")
    void createQuiz_slugConflict_appendsNumber() {
        QuizConfigRequest req = buildRequest("Quiz");
        QuizConfig saved = buildQuiz(1L, "Quiz", "quiz-2");

        when(quizConfigRepository.existsBySlug("quiz")).thenReturn(true);
        when(quizConfigRepository.existsBySlug("quiz-2")).thenReturn(false);
        when(quizConfigRepository.save(any())).thenReturn(saved);

        QuizConfigResponse result = service.createQuiz(req);

        assertThat(result.slug()).isEqualTo("quiz-2");
    }

    @Test
    @DisplayName("createQuiz: usa slug informado se presente")
    void createQuiz_withExplicitSlug() {
        QuizConfigRequest req = new QuizConfigRequest("Meu Quiz", "meu-slug-custom", 30, 1000, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        QuizConfig saved = buildQuiz(1L, "Meu Quiz", "meu-slug-custom");

        when(quizConfigRepository.existsBySlug("meu-slug-custom")).thenReturn(false);
        when(quizConfigRepository.save(any())).thenReturn(saved);

        QuizConfigResponse result = service.createQuiz(req);
        assertThat(result.slug()).isEqualTo("meu-slug-custom");
    }

    // ── updateQuiz ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateQuiz: atualiza campos e questões")
    void updateQuiz_success() {
        QuizConfig existing = buildQuiz(1L, "Antigo", "antigo");
        QuizConfigRequest req = buildRequest("Novo Nome");
        QuizConfig updated = buildQuiz(1L, "Novo Nome", "antigo");

        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(quizConfigRepository.save(any())).thenReturn(updated);

        QuizConfigResponse result = service.updateQuiz(1L, req);

        assertThat(result.name()).isEqualTo("Novo Nome");
    }

    @Test
    @DisplayName("updateQuiz: lança exceção se slug já está em uso por outro quiz")
    void updateQuiz_slugConflict_throws() {
        QuizConfig existing = buildQuiz(1L, "Quiz", "slug-atual");
        QuizConfigRequest req = new QuizConfigRequest("Quiz", "outro-slug", 30, 1000, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(quizConfigRepository.existsBySlug("outro-slug")).thenReturn(true);

        assertThatThrownBy(() -> service.updateQuiz(1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Slug já está em uso");
    }

    @Test
    @DisplayName("updateQuiz: não troca slug se for idêntico ao atual")
    void updateQuiz_sameSlug_noConflict() {
        QuizConfig existing = buildQuiz(1L, "Quiz", "meu-slug");
        QuizConfigRequest req = new QuizConfigRequest("Quiz", "meu-slug", 30, 1000, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        QuizConfig saved = buildQuiz(1L, "Quiz", "meu-slug");

        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(quizConfigRepository.save(any())).thenReturn(saved);

        assertThatCode(() -> service.updateQuiz(1L, req)).doesNotThrowAnyException();
        verify(quizConfigRepository, never()).existsBySlug(anyString());
    }

    // ── deleteQuiz ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteQuiz: deleta quando não está em uso")
    void deleteQuiz_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(templateRepository.existsByQuiz(quiz)).thenReturn(false);

        assertThatCode(() -> service.deleteQuiz(1L)).doesNotThrowAnyException();
        verify(quizConfigRepository).delete(quiz);
    }

    @Test
    @DisplayName("deleteQuiz: lança exceção se quiz está vinculado a template")
    void deleteQuiz_inUse_throws() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(templateRepository.existsByQuiz(quiz)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteQuiz(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("vinculado");
    }

    @Test
    @DisplayName("deleteQuiz: lança exceção se quiz não encontrado")
    void deleteQuiz_notFound_throws() {
        when(quizConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteQuiz(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ── getQuizById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getQuizById: retorna quiz com respostas corretas")
    void getQuizById_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));

        QuizConfigResponse result = service.getQuizById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.quizLink()).contains("https://test.example.com/quiz/quiz");
    }

    // ── assignQuizToTemplate / unassign ───────────────────────────────────────

    @Test
    @DisplayName("assignQuizToTemplate: vincula quiz ao template")
    void assignQuizToTemplate_success() {
        FormTemplate template = FormTemplate.builder().id(10L).name("Template").build();
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");

        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(templateRepository.save(any())).thenReturn(template);

        assertThatCode(() -> service.assignQuizToTemplate(10L, 1L)).doesNotThrowAnyException();
        assertThat(template.getQuiz()).isEqualTo(quiz);
    }

    @Test
    @DisplayName("unassignQuizFromTemplate: desvincula quiz do template")
    void unassignQuizFromTemplate_success() {
        FormTemplate template = FormTemplate.builder().id(10L).name("Template").build();
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        template.setQuiz(quiz);

        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenReturn(template);

        assertThatCode(() -> service.unassignQuizFromTemplate(10L)).doesNotThrowAnyException();
        assertThat(template.getQuiz()).isNull();
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleActive: inverte estado ativo do quiz")
    void toggleActive_fromActiveToInactive() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        quiz.setActive(true);

        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizConfigResponse result = service.toggleActive(1L);

        assertThat(result.active()).isFalse();
    }

    // ── getQuizBySlug ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getQuizBySlug: retorna quiz público sem respostas corretas")
    void getQuizBySlug_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "meu-quiz");

        when(quizConfigRepository.findBySlug("meu-quiz")).thenReturn(Optional.of(quiz));

        QuizConfigResponse result = service.getQuizBySlug("meu-quiz");

        assertThat(result.slug()).isEqualTo("meu-quiz");
    }

    @Test
    @DisplayName("getQuizBySlug: lança exceção se quiz inativo")
    void getQuizBySlug_inactive_throws() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "inativo");
        quiz.setActive(false);

        when(quizConfigRepository.findBySlug("inativo")).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> service.getQuizBySlug("inativo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("getQuizBySlug: lança exceção se slug não encontrado")
    void getQuizBySlug_notFound_throws() {
        when(quizConfigRepository.findBySlug("nao-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuizBySlug("nao-existe"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── startSession ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("startSession: cria sessão de jogador")
    void startSession_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "meu-quiz");
        StartQuizSessionRequest req = new StartQuizSessionRequest("João", "joao@email.com");
        QuizSession session = QuizSession.builder()
                .id(1L).quizConfig(quiz).playerName("João")
                .playerContact("joao@email.com").totalQuestions(0)
                .build();

        when(quizConfigRepository.findBySlug("meu-quiz")).thenReturn(Optional.of(quiz));
        when(sessionRepository.save(any())).thenReturn(session);

        QuizSessionResponse result = service.startSession("meu-quiz", req);

        assertThat(result.playerName()).isEqualTo("João");
    }

    // ── submitAnswer ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("submitAnswer: resposta correta adiciona pontos")
    void submitAnswer_correct_addsPoints() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        quiz.setTimePerQuestion(30);
        quiz.setPointsPerQuestion(1000);

        QuizQuestion question = buildQuestion(10L, quiz);
        quiz.getQuestions().add(question);

        QuizOption correctOpt = buildOption(100L, question, true);
        QuizOption wrongOpt = buildOption(101L, question, false);
        question.getOptions().add(correctOpt);
        question.getOptions().add(wrongOpt);

        QuizSession session = QuizSession.builder()
                .id(5L).quizConfig(quiz).playerName("Ana")
                .totalScore(0).correctAnswers(0).completed(false)
                .build();

        SubmitAnswerRequest req = new SubmitAnswerRequest(10L, 100L, 5000L);

        when(sessionRepository.findById(5L)).thenReturn(Optional.of(session));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerResultResponse result = service.submitAnswer(5L, req);

        assertThat(result.correct()).isTrue();
        assertThat(result.pointsEarned()).isGreaterThan(0);
    }

    @Test
    @DisplayName("submitAnswer: resposta errada não adiciona pontos")
    void submitAnswer_wrong_noPoints() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        quiz.setTimePerQuestion(30);
        quiz.setPointsPerQuestion(1000);

        QuizQuestion question = buildQuestion(10L, quiz);
        quiz.getQuestions().add(question);

        QuizOption correctOpt = buildOption(100L, question, true);
        QuizOption wrongOpt = buildOption(101L, question, false);
        question.getOptions().add(correctOpt);
        question.getOptions().add(wrongOpt);

        QuizSession session = QuizSession.builder()
                .id(5L).quizConfig(quiz).playerName("Ana")
                .totalScore(0).correctAnswers(0).completed(false)
                .build();

        // Escolhe opção errada
        SubmitAnswerRequest req = new SubmitAnswerRequest(10L, 101L, 5000L);

        when(sessionRepository.findById(5L)).thenReturn(Optional.of(session));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerResultResponse result = service.submitAnswer(5L, req);

        assertThat(result.correct()).isFalse();
        assertThat(result.pointsEarned()).isEqualTo(0);
    }

    @Test
    @DisplayName("submitAnswer: sem opção selecionada (tempo esgotado) não marca ponto")
    void submitAnswer_noOption_noPoints() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        quiz.setTimePerQuestion(30);
        quiz.setPointsPerQuestion(1000);

        QuizQuestion question = buildQuestion(10L, quiz);
        quiz.getQuestions().add(question);
        question.getOptions().add(buildOption(100L, question, true));

        QuizSession session = QuizSession.builder()
                .id(5L).quizConfig(quiz).playerName("Bob")
                .totalScore(0).correctAnswers(0).completed(false)
                .build();

        // optionId null = tempo esgotou
        SubmitAnswerRequest req = new SubmitAnswerRequest(10L, null, 30000L);

        when(sessionRepository.findById(5L)).thenReturn(Optional.of(session));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerResultResponse result = service.submitAnswer(5L, req);

        assertThat(result.correct()).isFalse();
        assertThat(result.pointsEarned()).isEqualTo(0);
    }

    @Test
    @DisplayName("submitAnswer: lança exceção se sessão já finalizada")
    void submitAnswer_alreadyCompleted_throws() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        QuizSession session = QuizSession.builder()
                .id(5L).quizConfig(quiz).completed(true).build();

        when(sessionRepository.findById(5L)).thenReturn(Optional.of(session));

        SubmitAnswerRequest req = new SubmitAnswerRequest(10L, 100L, 5000L);
        assertThatThrownBy(() -> service.submitAnswer(5L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("já finalizada");
    }

    // ── completeSession ───────────────────────────────────────────────────────

    @Test
    @DisplayName("completeSession: marca sessão como concluída e calcula posição")
    void completeSession_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        QuizSession session = QuizSession.builder()
                .id(5L).quizConfig(quiz).playerName("Ana")
                .totalScore(1500).correctAnswers(2).totalQuestions(3)
                .completed(false).build();
        QuizSession completed = QuizSession.builder()
                .id(5L).quizConfig(quiz).playerName("Ana")
                .totalScore(1500).correctAnswers(2).totalQuestions(3)
                .completed(true).build();

        when(sessionRepository.findById(5L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(completed);
        when(sessionRepository.findRanking(eq(quiz), any()))
                .thenReturn(List.of(completed));

        QuizSessionResponse result = service.completeSession(5L);

        assertThat(result.completed()).isTrue();
        assertThat(result.rankPosition()).isEqualTo(1);
    }

    // ── getRanking ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRanking: retorna ranking com posições")
    void getRanking_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "meu-quiz");
        QuizSession s = QuizSession.builder()
                .id(1L).quizConfig(quiz).playerName("Top1")
                .totalScore(2000).correctAnswers(3).totalQuestions(3)
                .completed(true).build();

        when(quizConfigRepository.findBySlug("meu-quiz")).thenReturn(Optional.of(quiz));
        when(sessionRepository.findRanking(eq(quiz), any())).thenReturn(List.of(s));
        when(sessionRepository.countByQuizConfigAndCompletedTrue(quiz)).thenReturn(1L);

        RankingResponse result = service.getRanking("meu-quiz", 10);

        assertThat(result.templateName()).isEqualTo("Quiz");
        assertThat(result.totalParticipants()).isEqualTo(1);
        assertThat(result.top()).hasSize(1);
    }

    // ── getAdminReport ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAdminReport: retorna relatório completo do quiz")
    void getAdminReport_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        QuizSession s = QuizSession.builder()
                .id(1L).quizConfig(quiz).playerName("Admin")
                .totalScore(500).correctAnswers(1).totalQuestions(2)
                .completed(true).build();

        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(sessionRepository.findByQuizConfigAndCompletedTrueOrderByTotalScoreDescCompletedAtAsc(quiz))
                .thenReturn(List.of(s));

        RankingResponse result = service.getAdminReport(1L);

        assertThat(result.totalParticipants()).isEqualTo(1);
        assertThat(result.top()).hasSize(1);
    }

    // ── resetRanking ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetRanking: deleta todas as sessões do quiz")
    void resetRanking_success() {
        QuizConfig quiz = buildQuiz(1L, "Quiz", "quiz");
        QuizSession s = QuizSession.builder().id(1L).quizConfig(quiz)
                .completed(true).playerName("Ana").build();

        when(quizConfigRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(sessionRepository.findByQuizConfigAndCompletedTrueOrderByTotalScoreDescCompletedAtAsc(quiz))
                .thenReturn(List.of(s));

        service.resetRanking(1L);

        verify(sessionRepository).deleteAll(List.of(s));
    }
}
