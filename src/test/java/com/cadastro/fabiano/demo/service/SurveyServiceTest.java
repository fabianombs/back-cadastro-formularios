package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateSurveyRequest;
import com.cadastro.fabiano.demo.dto.request.SubmitSurveyRequest;
import com.cadastro.fabiano.demo.dto.response.SurveyConfigResponse;
import com.cadastro.fabiano.demo.dto.response.SurveyReportResponse;
import com.cadastro.fabiano.demo.entity.SurveyConfig;
import com.cadastro.fabiano.demo.entity.SurveyResponse;
import com.cadastro.fabiano.demo.repository.SurveyConfigRepository;
import com.cadastro.fabiano.demo.repository.SurveyResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock private SurveyConfigRepository surveyRepo;
    @Mock private SurveyResponseRepository responseRepo;

    @InjectMocks
    private SurveyService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private SurveyConfig buildSurvey(Long id, String name, String slug) {
        return SurveyConfig.builder()
                .id(id).name(name).slug(slug)
                .welcomeTitle("Como foi?")
                .questionText("Qual sua satisfação?")
                .showComment(false)
                .thankYouMsg("Obrigado!")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CreateSurveyRequest buildRequest(String name, String slug) {
        return new CreateSurveyRequest(name, slug, "Empresa X", null,
                "Como foi?", "Qual sua satisfação?", false, "Obrigado!",
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAll: retorna todas as pesquisas")
    void listAll_returnsAll() {
        when(surveyRepo.findAll()).thenReturn(List.of(
                buildSurvey(1L, "NPS 2024", "nps-2024"),
                buildSurvey(2L, "Evento", "evento")));

        List<SurveyConfigResponse> result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("NPS 2024");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: cria pesquisa com slug gerado do nome")
    void create_generatesSlug() {
        CreateSurveyRequest req = buildRequest("Pesquisa Evento", null);
        SurveyConfig saved = buildSurvey(1L, "Pesquisa Evento", "pesquisa-evento");

        when(surveyRepo.existsBySlug("pesquisa-evento")).thenReturn(false);
        when(surveyRepo.save(any())).thenReturn(saved);

        SurveyConfigResponse result = service.create(req);

        assertThat(result.slug()).isEqualTo("pesquisa-evento");
        assertThat(result.publicLink()).contains("/survey/pesquisa-evento");
        verify(surveyRepo).save(any());
    }

    @Test
    @DisplayName("create: slug com sufixo quando já existe")
    void create_slugConflict_appendsNumber() {
        CreateSurveyRequest req = buildRequest("NPS", "nps");
        SurveyConfig saved = buildSurvey(1L, "NPS", "nps-2");

        when(surveyRepo.existsBySlug("nps")).thenReturn(true);
        when(surveyRepo.existsBySlug("nps-2")).thenReturn(false);
        when(surveyRepo.save(any())).thenReturn(saved);

        SurveyConfigResponse result = service.create(req);

        assertThat(result.slug()).isEqualTo("nps-2");
    }

    @Test
    @DisplayName("create: usa slug informado quando fornecido")
    void create_usesProvidedSlug() {
        CreateSurveyRequest req = buildRequest("Evento", "meu-slug");
        SurveyConfig saved = buildSurvey(1L, "Evento", "meu-slug");

        when(surveyRepo.existsBySlug("meu-slug")).thenReturn(false);
        when(surveyRepo.save(any())).thenReturn(saved);

        SurveyConfigResponse result = service.create(req);
        assertThat(result.slug()).isEqualTo("meu-slug");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: retorna pesquisa existente")
    void getById_success() {
        when(surveyRepo.findById(1L)).thenReturn(Optional.of(buildSurvey(1L, "NPS", "nps")));

        SurveyConfigResponse result = service.getById(1L);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById: lança 404 quando não encontrado")
    void getById_notFound() {
        when(surveyRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: atualiza campos da pesquisa")
    void update_success() {
        SurveyConfig existing = buildSurvey(1L, "Antigo", "antigo");
        CreateSurveyRequest req = buildRequest("Novo Nome", null);
        SurveyConfig saved = buildSurvey(1L, "Novo Nome", "antigo");

        when(surveyRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(surveyRepo.save(any())).thenReturn(saved);

        SurveyConfigResponse result = service.update(1L, req);
        assertThat(result.name()).isEqualTo("Novo Nome");
    }

    @Test
    @DisplayName("update: lança conflito se novo slug já existe em outro registro")
    void update_slugConflict() {
        SurveyConfig existing = buildSurvey(1L, "NPS", "nps");
        SurveyConfig other = buildSurvey(2L, "Outro", "novo-slug");
        CreateSurveyRequest req = new CreateSurveyRequest("NPS", "novo-slug",
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);

        when(surveyRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(surveyRepo.existsBySlug("novo-slug")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slug já está em uso");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: exclui pesquisa existente")
    void delete_success() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        when(surveyRepo.findById(1L)).thenReturn(Optional.of(survey));

        service.delete(1L);
        verify(surveyRepo).delete(survey);
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleActive: desativa pesquisa ativa")
    void toggleActive_deactivates() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        assertThat(survey.getActive()).isTrue();

        when(surveyRepo.findById(1L)).thenReturn(Optional.of(survey));
        when(surveyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SurveyConfigResponse result = service.toggleActive(1L);
        assertThat(result.active()).isFalse();
    }

    @Test
    @DisplayName("toggleActive: reativa pesquisa inativa")
    void toggleActive_reactivates() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        survey.setActive(false);

        when(surveyRepo.findById(1L)).thenReturn(Optional.of(survey));
        when(surveyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SurveyConfigResponse result = service.toggleActive(1L);
        assertThat(result.active()).isTrue();
    }

    // ── getBySlug ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBySlug: retorna pesquisa ativa pelo slug")
    void getBySlug_success() {
        when(surveyRepo.findBySlug("nps")).thenReturn(Optional.of(buildSurvey(1L, "NPS", "nps")));

        SurveyConfigResponse result = service.getBySlug("nps");
        assertThat(result.slug()).isEqualTo("nps");
    }

    @Test
    @DisplayName("getBySlug: lança 404 quando slug inexistente")
    void getBySlug_notFound() {
        when(surveyRepo.findBySlug("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBySlug("x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("getBySlug: lança 410 quando pesquisa inativa")
    void getBySlug_inactive() {
        SurveyConfig inactive = buildSurvey(1L, "NPS", "nps");
        inactive.setActive(false);
        when(surveyRepo.findBySlug("nps")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getBySlug("nps"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── submitResponse ────────────────────────────────────────────────────────

    @Test
    @DisplayName("submitResponse: salva resposta válida")
    void submitResponse_success() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        SubmitSurveyRequest req = new SubmitSurveyRequest(5, "Ótimo!", "João", null);

        when(surveyRepo.findBySlug("nps")).thenReturn(Optional.of(survey));
        when(responseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submitResponse("nps", req);
        verify(responseRepo).save(any(SurveyResponse.class));
    }

    @Test
    @DisplayName("submitResponse: lança 400 se score inválido")
    void submitResponse_invalidScore() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        SubmitSurveyRequest req = new SubmitSurveyRequest(6, null, null, null);

        when(surveyRepo.findBySlug("nps")).thenReturn(Optional.of(survey));

        assertThatThrownBy(() -> service.submitResponse("nps", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Score deve ser entre 1 e 5");
    }

    @Test
    @DisplayName("submitResponse: lança 410 quando pesquisa inativa")
    void submitResponse_inactiveSurvey() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        survey.setActive(false);
        SubmitSurveyRequest req = new SubmitSurveyRequest(4, null, null, null);

        when(surveyRepo.findBySlug("nps")).thenReturn(Optional.of(survey));

        assertThatThrownBy(() -> service.submitResponse("nps", req))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── getReport ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getReport: retorna relatório com distribuição e média")
    void getReport_success() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");
        SurveyResponse r1 = SurveyResponse.builder().id(1L).survey(survey).score(5)
                .comment("Ótimo").createdAt(LocalDateTime.now()).build();
        SurveyResponse r2 = SurveyResponse.builder().id(2L).survey(survey).score(3)
                .createdAt(LocalDateTime.now()).build();

        when(surveyRepo.findById(1L)).thenReturn(Optional.of(survey));
        when(responseRepo.findBySurveyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(r1, r2));
        when(responseRepo.avgScoreBySurveyId(1L)).thenReturn(4.0);
        when(responseRepo.countByScoreGrouped(1L)).thenReturn(List.of(
                new Object[]{5, 1L},
                new Object[]{3, 1L}
        ));

        SurveyReportResponse result = service.getReport(1L);

        assertThat(result.totalResponses()).isEqualTo(2);
        assertThat(result.averageScore()).isEqualTo(4.0);
        assertThat(result.scoreDistribution()).containsKey("Muito Satisfeito");
        assertThat(result.scoreDistribution().get("Muito Satisfeito")).isEqualTo(1L);
        assertThat(result.responses()).hasSize(2);
    }

    @Test
    @DisplayName("getReport: média zero quando sem respostas")
    void getReport_noResponses_avgZero() {
        SurveyConfig survey = buildSurvey(1L, "NPS", "nps");

        when(surveyRepo.findById(1L)).thenReturn(Optional.of(survey));
        when(responseRepo.findBySurveyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(responseRepo.avgScoreBySurveyId(1L)).thenReturn(null);
        when(responseRepo.countByScoreGrouped(1L)).thenReturn(List.of());

        SurveyReportResponse result = service.getReport(1L);

        assertThat(result.totalResponses()).isEqualTo(0);
        assertThat(result.averageScore()).isEqualTo(0.0);
    }
}
