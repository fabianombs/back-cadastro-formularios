package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.QuizRegisterField;
import com.cadastro.fabiano.demo.dto.request.*;
import com.cadastro.fabiano.demo.dto.response.*;
import com.cadastro.fabiano.demo.entity.*;
import com.cadastro.fabiano.demo.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class QuizService {

    private final QuizConfigRepository quizConfigRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizAnswerRepository answerRepository;
    private final FormTemplateRepository templateRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // URL do frontend — links públicos do quiz apontam para o Angular, não para a API
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public QuizService(QuizConfigRepository quizConfigRepository,
                       QuizSessionRepository sessionRepository,
                       QuizAnswerRepository answerRepository,
                       FormTemplateRepository templateRepository) {
        this.quizConfigRepository = quizConfigRepository;
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.templateRepository = templateRepository;
    }

    // ── CRUD de quizzes independentes ─────────────────────────────────────────

    /** Lista todos os quizzes da biblioteca */
    // toConfigResponse percorre questions e options, ambos LAZY (FABIANO-37).
    @Transactional(readOnly = true)
    public List<QuizConfigResponse> listAll() {
        return quizConfigRepository.findAll().stream()
                .map(q -> toConfigResponse(q, true))
                .toList();
    }

    /** Cria um quiz standalone (sem vínculo com template) */
    @Transactional
    public QuizConfigResponse createQuiz(QuizConfigRequest request) {
        String slug = buildUniqueSlug(request.slug() != null ? request.slug() : request.name());

        QuizConfig quiz = QuizConfig.builder()
                .name(request.name())
                .slug(slug)
                .timePerQuestion(request.timePerQuestion() > 0 ? request.timePerQuestion() : 30)
                .pointsPerQuestion(request.pointsPerQuestion() > 0 ? request.pointsPerQuestion() : 1000)
                .active(true)
                .build();

        // Aplica aparência visual se fornecida
        applyAppearance(quiz, request);
        applyQuestions(quiz, request);
        return toConfigResponse(quizConfigRepository.save(quiz), true);
    }

    /** Atualiza nome, slug, configurações e questões de um quiz */
    @Transactional
    public QuizConfigResponse updateQuiz(Long quizId, QuizConfigRequest request) {
        QuizConfig quiz = findQuizOrThrow(quizId);

        if (request.name() != null && !request.name().isBlank()) quiz.setName(request.name());

        // Slug: só atualiza se foi enviado e diferente do atual
        if (request.slug() != null && !request.slug().isBlank() && !request.slug().equals(quiz.getSlug())) {
            String newSlug = toSlug(request.slug());
            if (quizConfigRepository.existsBySlug(newSlug)) {
                throw new RuntimeException("Slug já está em uso: " + newSlug);
            }
            quiz.setSlug(newSlug);
        }

        quiz.setTimePerQuestion(request.timePerQuestion());
        quiz.setPointsPerQuestion(request.pointsPerQuestion());
        quiz.setActive(true);
        quiz.getQuestions().clear();

        // Aplica aparência visual se fornecida
        applyAppearance(quiz, request);
        applyQuestions(quiz, request);
        return toConfigResponse(quizConfigRepository.save(quiz), true);
    }

    /** Remove um quiz (desde que não esteja associado a nenhum template) */
    @Transactional
    public void deleteQuiz(Long quizId) {
        QuizConfig quiz = findQuizOrThrow(quizId);
        // Verifica se algum template usa este quiz
        boolean inUse = templateRepository.existsByQuiz(quiz);
        if (inUse) {
            throw new RuntimeException("Este quiz está vinculado a um ou mais templates. Desvincule primeiro.");
        }
        quizConfigRepository.delete(quiz);
    }

    /** Busca um quiz pelo ID (para edição admin) */
    @Transactional(readOnly = true)
    public QuizConfigResponse getQuizById(Long quizId) {
        return toConfigResponse(findQuizOrThrow(quizId), true);
    }

    // ── Associação quiz ↔ template ────────────────────────────────────────────

    /** Vincula um quiz a um template */
    @Transactional
    public void assignQuizToTemplate(Long templateId, Long quizId) {
        FormTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));
        QuizConfig quiz = findQuizOrThrow(quizId);
        template.setQuiz(quiz);
        templateRepository.save(template);
    }

    /** Remove a associação de quiz de um template */
    @Transactional
    public void unassignQuizFromTemplate(Long templateId) {
        FormTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));
        template.setQuiz(null);
        templateRepository.save(template);
    }

    // ── Ativar / desativar quiz ───────────────────────────────────────────────

    @Transactional
    public QuizConfigResponse toggleActive(Long quizId) {
        QuizConfig quiz = findQuizOrThrow(quizId);
        quiz.setActive(!quiz.isActive());
        return toConfigResponse(quizConfigRepository.save(quiz), true);
    }

    // ── Fluxo público: jogador acessa pelo slug ───────────────────────────────

    /** Busca quiz público pelo slug (sem revelar respostas corretas) */
    @Transactional(readOnly = true)
    public QuizConfigResponse getQuizBySlug(String slug) {
        QuizConfig quiz = quizConfigRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Quiz não encontrado"));
        if (!quiz.isActive()) throw new RuntimeException("Quiz inativo");
        return toConfigResponse(quiz, false);
    }

    /** Inicia uma sessão de jogador */
    @Transactional
    public QuizSessionResponse startSession(String slug, StartQuizSessionRequest request) {
        QuizConfig quiz = quizConfigRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Quiz não encontrado"));
        if (!quiz.isActive()) throw new RuntimeException("Quiz inativo");

        QuizSession session = QuizSession.builder()
                .quizConfig(quiz)
                .playerName(request.playerName())
                .playerContact(request.playerContact())
                .totalQuestions(quiz.getQuestions().size())
                .build();
        // Sem "fields" o jogador veio de um formulário (dados já coletados lá)
        if (hasRegisterFields(quiz) && request.fields() != null) {
            applyRegisterData(session, quiz.getRegisterFields(), request.fields());
        }

        return toPublicSessionResponse(sessionRepository.save(session), null);
    }

    /** Submete resposta de uma pergunta */
    @Transactional
    public AnswerResultResponse submitAnswer(Long sessionId, SubmitAnswerRequest request) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sessão não encontrada"));

        if (session.isCompleted()) throw new RuntimeException("Sessão já finalizada");

        QuizConfig quiz = session.getQuizConfig();

        QuizQuestion question = quiz.getQuestions().stream()
                .filter(q -> q.getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Pergunta não encontrada"));

        // Reenvio (a resposta chegou, mas a confirmação se perdeu na rede): devolve o que
        // já valeu, sem pontuar de novo
        QuizAnswer previous = answerRepository.findFirstBySessionAndQuestion(session, question).orElse(null);
        if (previous != null) {
            return new AnswerResultResponse(previous.isCorrect(), correctOptionId(question),
                    previous.getPointsEarned(), session.getTotalScore(), question.getExplanation());
        }

        QuizOption chosenOption = null;
        boolean correct = false;
        if (request.optionId() != null) {
            chosenOption = question.getOptions().stream()
                    .filter(o -> o.getId().equals(request.optionId()))
                    .findFirst().orElse(null);
            correct = chosenOption != null && chosenOption.isCorrect();
        }

        // Pontuação com bônus de velocidade (até 50% extra)
        int points = 0;
        if (correct) {
            long timeLimit = quiz.getTimePerQuestion() * 1000L;
            double ratio = Math.max(0, 1.0 - (request.timeTakenMs() / (double) timeLimit));
            points = (int) (quiz.getPointsPerQuestion() * (1.0 + ratio * 0.5));
        }

        QuizAnswer answer = QuizAnswer.builder()
                .session(session)
                .question(question)
                .option(chosenOption)
                .correct(correct)
                .pointsEarned(points)
                .timeTakenMs(request.timeTakenMs())
                .build();
        answerRepository.save(answer);

        session.setTotalScore(session.getTotalScore() + points);
        if (correct) session.setCorrectAnswers(session.getCorrectAnswers() + 1);
        sessionRepository.save(session);

        return new AnswerResultResponse(correct, correctOptionId(question), points, session.getTotalScore(),
                question.getExplanation());
    }

    private static Long correctOptionId(QuizQuestion question) {
        return question.getOptions().stream()
                .filter(QuizOption::isCorrect)
                .map(QuizOption::getId)
                .findFirst().orElse(null);
    }

    /** Finaliza sessão e calcula posição no ranking */
    @Transactional
    public QuizSessionResponse completeSession(Long sessionId) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Sessão não encontrada"));

        // Reenvio não muda o horário de conclusão (é critério de desempate do ranking)
        if (!session.isCompleted()) {
            session.setCompleted(true);
            session.setCompletedAt(LocalDateTime.now());
            session = sessionRepository.save(session);
        }

        List<QuizSession> ranking = sessionRepository.findRanking(
                session.getQuizConfig(), PageRequest.of(0, 1000));
        Integer position = null;
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getId().equals(session.getId())) {
                position = i + 1;
                break;
            }
        }
        return toPublicSessionResponse(session, position);
    }

    // ── Ranking e relatório ───────────────────────────────────────────────────

    public RankingResponse getRanking(String slug, int top) {
        QuizConfig quiz = quizConfigRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Quiz não encontrado"));

        List<QuizSession> sessions = sessionRepository.findRanking(quiz, PageRequest.of(0, top));
        long total = sessionRepository.countByQuizConfigAndCompletedTrue(quiz);

        List<QuizSessionResponse> list = new ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) list.add(toPublicSessionResponse(sessions.get(i), i + 1));

        return new RankingResponse(quiz.getName(), total, list);
    }

    public RankingResponse getAdminReport(Long quizId) {
        QuizConfig quiz = findQuizOrThrow(quizId);

        List<QuizSession> sessions = sessionRepository
                .findByQuizConfigAndCompletedTrueOrderByTotalScoreDescCompletedAtAsc(quiz);

        List<QuizSessionResponse> list = new ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) list.add(toSessionResponse(sessions.get(i), i + 1));

        return new RankingResponse(quiz.getName(), (long) sessions.size(), list);
    }

    /** Zera o ranking de um quiz */
    @Transactional
    public void resetRanking(Long quizId) {
        QuizConfig quiz = findQuizOrThrow(quizId);
        List<QuizSession> sessions = sessionRepository
                .findByQuizConfigAndCompletedTrueOrderByTotalScoreDescCompletedAtAsc(quiz);
        sessionRepository.deleteAll(sessions);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QuizConfig findQuizOrThrow(Long quizId) {
        return quizConfigRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz não encontrado: " + quizId));
    }

    // Sobrescreve todos os campos de aparência — frontend é a fonte da verdade
    // (guards "if != null" impediam limpar campos ao trocar, ex: imagem → gradiente)
    private void applyAppearance(QuizConfig quiz, QuizConfigRequest request) {
        quiz.setBackgroundColor(request.backgroundColor());
        quiz.setBackgroundGradient(request.backgroundGradient());
        quiz.setBackgroundImageUrl(request.backgroundImageUrl());
        quiz.setBackgroundImageMobileUrl(request.backgroundImageMobileUrl());
        quiz.setBackgroundImageTabletUrl(request.backgroundImageTabletUrl());
        quiz.setPrimaryColor(request.primaryColor());
        quiz.setTextColor(request.textColor());
        quiz.setCardColor(request.cardColor());
        quiz.setRegisterCardColor(request.registerCardColor());
        quiz.setInputColor(request.inputColor());
        quiz.setRankingCardColor(request.rankingCardColor());
        quiz.setButtonColor(request.buttonColor());
        quiz.setButtonTextColor(request.buttonTextColor());
        quiz.setReadyTitle(request.readyTitle());
        quiz.setReadyMessage(request.readyMessage());
        quiz.setThankYouEnabled(request.thankYouEnabled());
        quiz.setThankYouTitle(request.thankYouTitle());
        quiz.setThankYouSubtitle(request.thankYouSubtitle());
        quiz.setThankYouParagraph(request.thankYouParagraph());
        quiz.setThankYouBgColor(request.thankYouBgColor());
        quiz.setThankYouBgGradient(request.thankYouBgGradient());
        quiz.setThankYouBgImageUrl(request.thankYouBgImageUrl());
        quiz.setThankYouBgImageMobileUrl(request.thankYouBgImageMobileUrl());
        quiz.setThankYouBgImageTabletUrl(request.thankYouBgImageTabletUrl());
        quiz.setThankYouIconColor(request.thankYouIconColor());
        quiz.setThankYouTitleColor(request.thankYouTitleColor());
        quiz.setThankYouTextColor(request.thankYouTextColor());
        quiz.setThankYouFontFamily(request.thankYouFontFamily());
        quiz.setThankYouTitleFontSize(request.thankYouTitleFontSize());
        quiz.setLogoUrl(request.logoUrl());
        quiz.setRegisterTextColor(request.registerTextColor());
        quiz.setOptionTextColor(request.optionTextColor());
        quiz.setQuestionCardColor(request.questionCardColor());
        quiz.setSecondaryTextColor(request.secondaryTextColor());
        // Ausente (null) = mostra; só um false explícito esconde a logo
        quiz.setShowLogoRegister(!Boolean.FALSE.equals(request.showLogoRegister()));
        quiz.setShowLogoReady(!Boolean.FALSE.equals(request.showLogoReady()));
        quiz.setShowLogoQuestion(!Boolean.FALSE.equals(request.showLogoQuestion()));
        quiz.setShowLogoResult(!Boolean.FALSE.equals(request.showLogoResult()));
        quiz.setShowLogoRanking(!Boolean.FALSE.equals(request.showLogoRanking()));
        quiz.setFontUrl(validFontUrl(request.fontUrl()));
        quiz.setFontBoldUrl(validFontUrl(request.fontBoldUrl()));
        validateRegisterFields(request.registerFields());
        quiz.setRegisterFields(request.registerFields() == null || request.registerFields().isEmpty()
                ? null : request.registerFields());
    }

    // ── Fonte e campos do cadastro ────────────────────────────────────────────

    private static final Pattern FONT_URL = Pattern.compile("/quizzes/fonts/[0-9a-f-]{36}/[A-Za-z0-9._-]{1,100}");
    private static final Set<String> FIELD_TYPES = Set.of("text", "email", "tel", "number");
    private static final int MAX_FIELDS = 10;
    private static final int MAX_VALUE = 255;

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // Só fontes enviadas aqui: o link público não deve carregar arquivo de endereço qualquer
    private static String validFontUrl(String url) {
        String u = blankToNull(url);
        if (u != null && !FONT_URL.matcher(u).matches()) throw new RuntimeException("Fonte inválida. Envie o arquivo novamente.");
        return u;
    }

    private static boolean hasRegisterFields(QuizConfig quiz) {
        return quiz.getRegisterFields() != null && !quiz.getRegisterFields().isEmpty();
    }

    private static void validateRegisterFields(List<QuizRegisterField> fields) {
        if (fields == null || fields.isEmpty()) return;
        if (fields.size() > MAX_FIELDS) throw new RuntimeException("Use no máximo " + MAX_FIELDS + " campos no cadastro.");
        Set<String> ids = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (QuizRegisterField f : fields) {
            if (f.id() == null || f.id().isBlank() || !ids.add(f.id())) throw new RuntimeException("Campo de cadastro inválido.");
            if (f.label() == null || f.label().isBlank() || f.label().length() > 80) throw new RuntimeException("Dê um nome de até 80 letras a cada campo do cadastro.");
            if (!labels.add(f.label().trim().toLowerCase())) throw new RuntimeException("Há campos do cadastro com o mesmo nome: " + f.label());
            if (!FIELD_TYPES.contains(f.type())) throw new RuntimeException("Tipo de campo inválido: " + f.label());
        }
        if (fields.stream().noneMatch(f -> f.required() && f.showInRanking())) {
            throw new RuntimeException("Pelo menos um campo do cadastro deve ser obrigatório e aparecer no ranking.");
        }
    }

    private static void applyRegisterData(QuizSession session, List<QuizRegisterField> fields, Map<String, String> values) {
        Map<String, String> data = new LinkedHashMap<>();
        List<String> name = new ArrayList<>();
        String contact = "";
        for (QuizRegisterField f : fields) {
            String v = blankToNull(values.get(f.id()));
            if (v == null) {
                if (f.required()) throw new RuntimeException("Preencha o campo: " + f.label());
                continue;
            }
            if (v.length() > MAX_VALUE) throw new RuntimeException("Texto muito longo em: " + f.label());
            data.put(f.label(), v);
            if (f.showInRanking()) name.add(v);
            if (contact.isEmpty() && ("email".equals(f.type()) || "tel".equals(f.type()))) contact = v;
        }
        String fullName = String.join(" ", name);
        session.setPlayerName(fullName.length() > MAX_VALUE ? fullName.substring(0, MAX_VALUE) : fullName);
        session.setPlayerContact(contact);
        session.setRegisterData(data);
    }

    private void applyQuestions(QuizConfig quiz, QuizConfigRequest request) {
        if (request.questions() == null) return;
        for (QuizQuestionRequest qReq : request.questions()) {
            QuizQuestion q = QuizQuestion.builder()
                    .quizConfig(quiz)
                    .question(qReq.question())
                    .imageUrl(qReq.imageUrl())
                    .explanation(blankToNull(qReq.explanation()))
                    .orderIndex(qReq.orderIndex())
                    .build();
            if (qReq.options() != null) {
                for (QuizOptionRequest oReq : qReq.options()) {
                    q.getOptions().add(QuizOption.builder()
                            .question(q)
                            .optionText(oReq.optionText())
                            .correct(oReq.correct())
                            .orderIndex(oReq.orderIndex())
                            .build());
                }
            }
            quiz.getQuestions().add(q);
        }
    }

    private QuizConfigResponse toConfigResponse(QuizConfig quiz, boolean includeCorrect) {
        List<QuizQuestionResponse> questions = quiz.getQuestions().stream()
                .map(q -> new QuizQuestionResponse(
                        q.getId(), q.getQuestion(), q.getImageUrl(), q.getOrderIndex(),
                        q.getOptions().stream()
                                .map(o -> new QuizOptionResponse(
                                        o.getId(), o.getOptionText(), o.getOrderIndex(),
                                        includeCorrect ? o.isCorrect() : null))
                                .toList(),
                        includeCorrect ? q.getExplanation() : null))
                .toList();

        return new QuizConfigResponse(
                quiz.getId(),
                quiz.getName(),
                quiz.getSlug(),
                quiz.getTimePerQuestion(),
                quiz.getPointsPerQuestion(),
                quiz.isActive(),
                questions.size(),
                questions,
                frontendUrl + "/quiz/" + quiz.getSlug(),
                frontendUrl + "/quiz/" + quiz.getSlug() + "/ranking",
                quiz.getBackgroundColor(),
                quiz.getBackgroundGradient(),
                quiz.getBackgroundImageUrl(),
                quiz.getBackgroundImageMobileUrl(),
                quiz.getBackgroundImageTabletUrl(),
                quiz.getPrimaryColor(),
                quiz.getTextColor(),
                quiz.getCardColor(),
                quiz.getRegisterCardColor(),
                quiz.getInputColor(),
                quiz.getRankingCardColor(),
                quiz.getButtonColor(),
                quiz.getButtonTextColor(),
                quiz.getReadyTitle(),
                quiz.getReadyMessage(),
                quiz.isThankYouEnabled(),
                quiz.getThankYouTitle(),
                quiz.getThankYouSubtitle(),
                quiz.getThankYouParagraph(),
                quiz.getThankYouBgColor(),
                quiz.getThankYouBgGradient(),
                quiz.getThankYouBgImageUrl(),
                quiz.getThankYouBgImageMobileUrl(),
                quiz.getThankYouBgImageTabletUrl(),
                quiz.getThankYouIconColor(),
                quiz.getThankYouTitleColor(),
                quiz.getThankYouTextColor(),
                quiz.getThankYouFontFamily(),
                quiz.getThankYouTitleFontSize(),
                quiz.getLogoUrl(),
                quiz.getRegisterTextColor(),
                quiz.getOptionTextColor(),
                quiz.getQuestionCardColor(),
                quiz.getSecondaryTextColor(),
                quiz.isShowLogoRegister(),
                quiz.isShowLogoReady(),
                quiz.isShowLogoQuestion(),
                quiz.isShowLogoResult(),
                quiz.isShowLogoRanking(),
                quiz.getFontUrl(),
                quiz.getFontBoldUrl(),
                quiz.getRegisterFields()
        );
    }

    // Link público: contato e campos do cadastro são dados pessoais e não saem daqui
    private QuizSessionResponse toPublicSessionResponse(QuizSession s, Integer position) {
        return new QuizSessionResponse(
                s.getId(), s.getPlayerName(), null,
                s.getTotalScore(), s.getCorrectAnswers(), s.getTotalQuestions(),
                s.isCompleted(), s.getCompletedAt(), position, null);
    }

    private QuizSessionResponse toSessionResponse(QuizSession s, Integer position) {
        return new QuizSessionResponse(
                s.getId(), s.getPlayerName(), s.getPlayerContact(),
                s.getTotalScore(), s.getCorrectAnswers(), s.getTotalQuestions(),
                s.isCompleted(), s.getCompletedAt(), position, s.getRegisterData());
    }

    /** Gera slug a partir de texto, garantindo unicidade */
    private String buildUniqueSlug(String text) {
        String base = toSlug(text);
        if (!quizConfigRepository.existsBySlug(base)) return base;
        int i = 2;
        while (quizConfigRepository.existsBySlug(base + "-" + i)) i++;
        return base + "-" + i;
    }

    private static String toSlug(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String ascii = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        return ascii.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
