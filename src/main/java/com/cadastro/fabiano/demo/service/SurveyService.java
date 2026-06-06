package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateSurveyRequest;
import com.cadastro.fabiano.demo.dto.request.SubmitSurveyRequest;
import com.cadastro.fabiano.demo.dto.response.SurveyConfigResponse;
import com.cadastro.fabiano.demo.dto.response.SurveyReportResponse;
import com.cadastro.fabiano.demo.entity.SurveyConfig;
import com.cadastro.fabiano.demo.entity.SurveyResponse;
import com.cadastro.fabiano.demo.repository.SurveyConfigRepository;
import com.cadastro.fabiano.demo.repository.SurveyResponseRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SurveyService {

    private final SurveyConfigRepository surveyRepo;
    private final SurveyResponseRepository responseRepo;

    // Labels para cada score (1-5) usados no relatório e no Excel
    private static final Map<Integer, String> SCORE_LABELS = Map.of(
            5, "Muito Satisfeito",
            4, "Satisfeito",
            3, "Regular",
            2, "Insatisfeito",
            1, "Muito Insatisfeito"
    );

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public SurveyService(SurveyConfigRepository surveyRepo, SurveyResponseRepository responseRepo) {
        this.surveyRepo  = surveyRepo;
        this.responseRepo = responseRepo;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<SurveyConfigResponse> listAll() {
        return surveyRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SurveyConfigResponse create(CreateSurveyRequest req) {
        String slug = buildUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name());

        SurveyConfig survey = SurveyConfig.builder()
                .name(req.name())
                .slug(slug)
                .companyName(req.companyName())
                .companyLogoUrl(req.companyLogoUrl())
                .welcomeTitle(req.welcomeTitle() != null ? req.welcomeTitle() : "Como foi sua experiência?")
                .questionText(req.questionText() != null ? req.questionText() : "Quão satisfeito você está com nosso serviço hoje?")
                .showComment(req.showComment() != null ? req.showComment() : false)
                .thankYouMsg(req.thankYouMsg() != null ? req.thankYouMsg() : "Muito obrigado pela sua avaliação!")
                .active(true)
                .build();

        applyAppearance(survey, req);
        return toResponse(surveyRepo.save(survey));
    }

    public SurveyConfigResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public SurveyConfigResponse update(Long id, CreateSurveyRequest req) {
        SurveyConfig survey = findOrThrow(id);

        if (req.name() != null && !req.name().isBlank())         survey.setName(req.name());
        if (req.companyName() != null)                            survey.setCompanyName(req.companyName());
        if (req.companyLogoUrl() != null)                         survey.setCompanyLogoUrl(req.companyLogoUrl());
        if (req.welcomeTitle() != null && !req.welcomeTitle().isBlank()) survey.setWelcomeTitle(req.welcomeTitle());
        if (req.questionText() != null && !req.questionText().isBlank()) survey.setQuestionText(req.questionText());
        if (req.showComment() != null)                            survey.setShowComment(req.showComment());
        if (req.thankYouMsg() != null && !req.thankYouMsg().isBlank())   survey.setThankYouMsg(req.thankYouMsg());
        applyAppearance(survey, req);

        // Slug: só atualiza se enviado e diferente
        if (req.slug() != null && !req.slug().isBlank() && !req.slug().equals(survey.getSlug())) {
            String newSlug = toSlug(req.slug());
            if (surveyRepo.existsBySlug(newSlug))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug já está em uso");
            survey.setSlug(newSlug);
        }

        return toResponse(surveyRepo.save(survey));
    }

    @Transactional
    public void delete(Long id) {
        surveyRepo.delete(findOrThrow(id));
    }

    @Transactional
    public SurveyConfigResponse toggleActive(Long id) {
        SurveyConfig survey = findOrThrow(id);
        survey.setActive(!survey.getActive());
        return toResponse(surveyRepo.save(survey));
    }

    // ── Público ───────────────────────────────────────────────────────────────

    public SurveyConfigResponse getBySlug(String slug) {
        SurveyConfig survey = surveyRepo.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pesquisa não encontrada"));

        if (!survey.getActive())
            throw new ResponseStatusException(HttpStatus.GONE, "Pesquisa inativa");

        return toResponse(survey);
    }

    @Transactional
    public void submitResponse(String slug, SubmitSurveyRequest req) {
        SurveyConfig survey = surveyRepo.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pesquisa não encontrada"));

        if (!survey.getActive())
            throw new ResponseStatusException(HttpStatus.GONE, "Pesquisa inativa");

        if (req.score() == null || req.score() < 1 || req.score() > 5)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score deve ser entre 1 e 5");

        SurveyResponse response = SurveyResponse.builder()
                .survey(survey)
                .score(req.score())
                .comment(req.comment())
                .respondentRef(req.respondentRef())
                .sourceTemplateSlug(req.sourceTemplateSlug())
                .build();

        responseRepo.save(response);
    }

    // ── Relatório ─────────────────────────────────────────────────────────────

    public SurveyReportResponse getReport(Long id) {
        SurveyConfig survey = findOrThrow(id);

        List<SurveyResponse> all = responseRepo.findBySurveyIdOrderByCreatedAtDesc(id);
        long total = all.size();
        Double avg = responseRepo.avgScoreBySurveyId(id);

        // Distribuição agrupada por label na ordem decrescente de score
        Map<String, Long> distribution = new LinkedHashMap<>();
        List<Object[]> grouped = responseRepo.countByScoreGrouped(id);
        Map<Integer, Long> byScore = new HashMap<>();
        for (Object[] row : grouped) {
            byScore.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        // Garante que todos os 5 labels apareçam no mapa, mesmo com contagem zero
        for (int s = 5; s >= 1; s--) {
            distribution.put(SCORE_LABELS.get(s), byScore.getOrDefault(s, 0L));
        }

        List<SurveyReportResponse.SurveyResponseItem> items = all.stream()
                .map(r -> new SurveyReportResponse.SurveyResponseItem(
                        r.getId(),
                        r.getScore(),
                        SCORE_LABELS.getOrDefault(r.getScore(), "?"),
                        r.getComment(),
                        r.getRespondentRef(),
                        r.getSourceTemplateSlug(),
                        r.getCreatedAt()
                ))
                .toList();

        return new SurveyReportResponse(survey.getId(), survey.getName(), total,
                avg != null ? Math.round(avg * 100.0) / 100.0 : 0.0,
                distribution, items);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SurveyConfig findOrThrow(Long id) {
        return surveyRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pesquisa não encontrada"));
    }

    private SurveyConfigResponse toResponse(SurveyConfig s) {
        return new SurveyConfigResponse(
                s.getId(), s.getName(), s.getSlug(),
                s.getCompanyName(), s.getCompanyLogoUrl(),
                s.getWelcomeTitle(), s.getQuestionText(),
                s.getShowComment(), s.getThankYouMsg(), s.getActive(),
                frontendUrl + "/survey/" + s.getSlug(),
                frontendUrl + "/survey/" + s.getSlug() + "/report",
                s.getCreatedAt(), s.getUpdatedAt(),
                // Aparência
                s.getBackgroundColor(), s.getBackgroundGradient(), s.getBackgroundImageUrl(),
                s.getPrimaryColor(), s.getTextColor(), s.getCardColor(),
                s.getButtonColor(), s.getButtonTextColor(), s.getLogoBorderRadius(),
                // Posições livres
                s.getLogoPosX(), s.getLogoPosY(), s.getLogoWidth(),
                s.getCardPosX(), s.getCardPosY(),
                // Visibilidade do logo por tela
                s.getShowLogoWelcome(), s.getShowLogoRating(), s.getShowLogoThankyou(),
                // Ícones customizados
                s.getScore5ImageUrl(), s.getScore4ImageUrl(), s.getScore3ImageUrl(),
                s.getScore2ImageUrl(), s.getScore1ImageUrl(),
                // Labels editáveis
                s.getScore5Label(), s.getScore4Label(), s.getScore3Label(),
                s.getScore2Label(), s.getScore1Label(),
                // Subtítulos e botões
                s.getWelcomeSubtitle(), s.getThankyouSubtitle(),
                s.getWelcomeBtnText(), s.getRatingBtnText()
        );
    }

    private void applyAppearance(SurveyConfig s, CreateSurveyRequest req) {
        s.setBackgroundColor(req.backgroundColor());
        s.setBackgroundGradient(req.backgroundGradient());
        s.setBackgroundImageUrl(req.backgroundImageUrl());
        s.setPrimaryColor(req.primaryColor());
        s.setTextColor(req.textColor());
        s.setCardColor(req.cardColor());
        s.setButtonColor(req.buttonColor());
        s.setButtonTextColor(req.buttonTextColor());
        s.setLogoBorderRadius(req.logoBorderRadius());
        // Posições livres
        if (req.logoPosX() != null) s.setLogoPosX(req.logoPosX());
        if (req.logoPosY() != null) s.setLogoPosY(req.logoPosY());
        if (req.logoWidth() != null) s.setLogoWidth(req.logoWidth());
        if (req.cardPosX() != null) s.setCardPosX(req.cardPosX());
        if (req.cardPosY() != null) s.setCardPosY(req.cardPosY());
        // Visibilidade do logo por tela
        if (req.showLogoWelcome()  != null) s.setShowLogoWelcome(req.showLogoWelcome());
        if (req.showLogoRating()   != null) s.setShowLogoRating(req.showLogoRating());
        if (req.showLogoThankyou() != null) s.setShowLogoThankyou(req.showLogoThankyou());
        // Ícones customizados (null = manter atual; string vazia = limpar)
        if (req.score5ImageUrl() != null) s.setScore5ImageUrl(req.score5ImageUrl().isBlank() ? null : req.score5ImageUrl());
        if (req.score4ImageUrl() != null) s.setScore4ImageUrl(req.score4ImageUrl().isBlank() ? null : req.score4ImageUrl());
        if (req.score3ImageUrl() != null) s.setScore3ImageUrl(req.score3ImageUrl().isBlank() ? null : req.score3ImageUrl());
        if (req.score2ImageUrl() != null) s.setScore2ImageUrl(req.score2ImageUrl().isBlank() ? null : req.score2ImageUrl());
        if (req.score1ImageUrl() != null) s.setScore1ImageUrl(req.score1ImageUrl().isBlank() ? null : req.score1ImageUrl());
        // Labels dos scores
        if (req.score5Label() != null && !req.score5Label().isBlank()) s.setScore5Label(req.score5Label());
        if (req.score4Label() != null && !req.score4Label().isBlank()) s.setScore4Label(req.score4Label());
        if (req.score3Label() != null && !req.score3Label().isBlank()) s.setScore3Label(req.score3Label());
        if (req.score2Label() != null && !req.score2Label().isBlank()) s.setScore2Label(req.score2Label());
        if (req.score1Label() != null && !req.score1Label().isBlank()) s.setScore1Label(req.score1Label());
        // Subtítulos e botões
        if (req.welcomeSubtitle()  != null) s.setWelcomeSubtitle(req.welcomeSubtitle());
        if (req.thankyouSubtitle() != null) s.setThankyouSubtitle(req.thankyouSubtitle());
        if (req.welcomeBtnText()   != null && !req.welcomeBtnText().isBlank())  s.setWelcomeBtnText(req.welcomeBtnText());
        if (req.ratingBtnText()    != null && !req.ratingBtnText().isBlank())   s.setRatingBtnText(req.ratingBtnText());
    }

    private String buildUniqueSlug(String base) {
        String slug = toSlug(base);
        if (!surveyRepo.existsBySlug(slug)) return slug;

        // Adiciona sufixo numérico até encontrar slug disponível
        int i = 2;
        while (surveyRepo.existsBySlug(slug + "-" + i)) i++;
        return slug + "-" + i;
    }

    private String toSlug(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return Pattern.compile("[^\\p{ASCII}]").matcher(normalized).replaceAll("")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
