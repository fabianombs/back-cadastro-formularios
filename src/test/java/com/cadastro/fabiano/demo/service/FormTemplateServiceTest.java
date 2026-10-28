package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.DuplicateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.FormFieldRequest;
import com.cadastro.fabiano.demo.dto.request.ScheduleConfigRequest;
import com.cadastro.fabiano.demo.dto.request.UpdateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormField;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.AppointmentRepository;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.ClientRepository;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormTemplateServiceTest {

    @Mock
    private FormTemplateRepository templateRepository;

    @Mock
    private FormSubmissionRepository submissionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AttendanceRecordRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private MetricasDeNegocio metricas;

    @InjectMocks
    private FormTemplateService service;

    private Client client;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L)
                .name("Cliente Teste")
                .username("cliente_teste")
                .build();

        // Templates não têm quiz associado por padrão nos testes (quiz é null no entity)
    }

    // ─── createTemplate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createTemplate: cria template sem agenda")
    void createTemplate_success_noSchedule() {
        FormFieldRequest field = new FormFieldRequest("Nome", "text", true, null, 2, null);
        CreateFormTemplateRequest request = new CreateFormTemplateRequest(
                "Formulário de Contato", 1L, List.of(field), null, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        FormTemplate saved = buildTemplate("Formulário de Contato", "formulario-de-contato");

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(templateRepository.existsBySlug(anyString())).thenReturn(false);
        when(templateRepository.save(any())).thenReturn(saved);

        FormTemplateResponse response = service.createTemplate(request, 1L);

        assertThat(response.name()).isEqualTo("Formulário de Contato");
        assertThat(response.hasSchedule()).isFalse();
    }

    @Test
    @DisplayName("createTemplate: cria template com agenda")
    void createTemplate_success_withSchedule() {
        ScheduleConfigRequest sc = new ScheduleConfigRequest(
                LocalTime.of(8, 0), LocalTime.of(17, 0), 30, 7, 3, List.of("CPF"));

        CreateFormTemplateRequest request = new CreateFormTemplateRequest(
                "Agenda Médica", 1L, List.of(), sc, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        FormTemplate saved = buildTemplate("Agenda Médica", "agenda-medica");
        saved.setHasSchedule(true);
        saved.setScheduleStartTime(LocalTime.of(8, 0));
        saved.setScheduleEndTime(LocalTime.of(17, 0));
        saved.setSlotDurationMinutes(30);
        saved.setMaxDaysAhead(7);
        saved.setSlotCapacity(3);
        saved.setDedupFields(Set.of("CPF"));

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(templateRepository.existsBySlug(anyString())).thenReturn(false);
        when(templateRepository.save(any())).thenReturn(saved);

        FormTemplateResponse response = service.createTemplate(request, 1L);

        assertThat(response.hasSchedule()).isTrue();
        assertThat(response.scheduleConfig().slotCapacity()).isEqualTo(3);
    }

    @Test
    @DisplayName("createTemplate: gera slug único quando já existe conflito")
    void createTemplate_slugConflict_generatesUnique() {
        // "Formulario" (sem acento) → slug "formulario"
        CreateFormTemplateRequest request = new CreateFormTemplateRequest(
                "Formulario", 1L, List.of(), null, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        FormTemplate saved = buildTemplate("Formulario", "formulario-1");

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(templateRepository.existsBySlug("formulario")).thenReturn(true);
        when(templateRepository.existsBySlug("formulario-1")).thenReturn(false);
        when(templateRepository.save(any())).thenReturn(saved);

        FormTemplateResponse response = service.createTemplate(request, 1L);

        assertThat(response.name()).isEqualTo("Formulario");
    }

    @Test
    @DisplayName("createTemplate: lança exceção se cliente não encontrado")
    void createTemplate_clientNotFound_throws() {
        CreateFormTemplateRequest request = new CreateFormTemplateRequest(
                "Form", 99L, List.of(), null, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTemplate(request, 99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── findAllTemplates ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findAllTemplates: retorna página de templates")
    void findAllTemplates_success() {
        FormTemplate t = buildTemplate("Template A", "template-a");
        PageRequest pageable = PageRequest.of(0, 10);

        when(templateRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(t)));

        Page<FormTemplateResponse> result = service.findAllTemplates(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── findTemplatesByUsername ───────────────────────────────────────────────

    @Test
    @DisplayName("findTemplatesByUsername: retorna templates do usuário cliente")
    void findTemplatesByUsername_success() {
        User user = User.builder().id(1L).username("cliente_teste").build();
        FormTemplate t = buildTemplate("Meu Form", "meu-form");
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.findByUsername("cliente_teste")).thenReturn(Optional.of(user));
        when(clientRepository.findByUser(user)).thenReturn(Optional.of(client));
        when(templateRepository.findByClient(client, pageable)).thenReturn(new PageImpl<>(List.of(t)));

        Page<FormTemplateResponse> result = service.findTemplatesByUsername("cliente_teste", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findTemplatesByUsername: lança exceção se usuário não encontrado")
    void findTemplatesByUsername_userNotFound_throws() {
        when(userRepository.findByUsername("naoexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTemplatesByUsername("naoexiste", PageRequest.of(0, 10)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuário");
    }

    // ─── findBySlug ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findBySlug: retorna template pelo slug")
    void findBySlug_success() {
        FormTemplate t = buildTemplate("Cadastro", "cadastro");

        when(templateRepository.findBySlug("cadastro")).thenReturn(Optional.of(t));

        FormTemplateResponse response = service.findBySlug("cadastro");

        assertThat(response.slug()).isEqualTo("cadastro");
    }

    @Test
    @DisplayName("findBySlug: lança exceção se slug não encontrado")
    void findBySlug_notFound_throws() {
        when(templateRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySlug("inexistente"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── updateScheduleConfig ─────────────────────────────────────────────────

    @Test
    @DisplayName("updateScheduleConfig: atualiza configuração de agenda com sucesso")
    void updateScheduleConfig_success() {
        FormTemplate t = buildTemplate("Agenda", "agenda");

        ScheduleConfigRequest sc = new ScheduleConfigRequest(
                LocalTime.of(9, 0), LocalTime.of(18, 0), 60, 14, 5, List.of());

        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenReturn(t);

        FormTemplateResponse response = service.updateScheduleConfig(1L, sc);

        assertThat(response).isNotNull();
        verify(templateRepository).save(t);
    }

    @Test
    @DisplayName("updateScheduleConfig: slotCapacity=0 é substituído por 1")
    void updateScheduleConfig_zeroCapacity_defaultsToOne() {
        FormTemplate t = buildTemplate("Agenda", "agenda");

        ScheduleConfigRequest sc = new ScheduleConfigRequest(
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30, 7, 0, List.of());

        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            FormTemplate saved = inv.getArgument(0);
            assertThat(saved.getSlotCapacity()).isEqualTo(1);
            return saved;
        });

        service.updateScheduleConfig(1L, sc);
    }

    // ─── deleteTemplate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteTemplate: realiza hard delete do template e dependências")
    void deleteTemplate_success() {
        FormTemplate t = buildTemplate("Form Deletado", "form-deletado");

        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        // template não possui URLs de imagem, logo imageStorageService.delete não é chamado

        service.deleteTemplate(1L);

        verify(submissionRepository).deleteByTemplate_Id(1L);
        verify(appointmentRepository).deleteByFormTemplate(t);
        verify(attendanceRepository).deleteByFormTemplate(t);
        verify(templateRepository).delete(t);
        verifyNoInteractions(imageStorageService);
    }

    @Test
    @DisplayName("deleteTemplate: lança exceção se template não encontrado")
    void deleteTemplate_notFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTemplate(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("deleteTemplate: não apaga imagem que outro template (ex.: cópia) ainda usa")
    void deleteTemplate_sharedImage_keepsFile() {
        FormTemplate t = buildTemplate("Original", "original");
        t.setHeaderImageUrl("https://img/topo.png");
        t.setFooterImageUrl("https://img/rodape.png");

        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.countUsingImageUrlExcluding("https://img/topo.png", 1L)).thenReturn(1L);
        when(templateRepository.countUsingImageUrlExcluding("https://img/rodape.png", 1L)).thenReturn(0L);

        service.deleteTemplate(1L);

        verify(imageStorageService, never()).delete("https://img/topo.png");
        verify(imageStorageService).delete("https://img/rodape.png");
    }

    // ─── duplicateTemplate ────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicateTemplate: copia todas as configurações, com slug novo e sem viewToken")
    void duplicateTemplate_copiesEverything() throws Exception {
        FormTemplate original = buildFullTemplate();
        Client outro = Client.builder().id(2L).name("Outro Cliente").username("outro").build();

        when(templateRepository.findById(1L)).thenReturn(Optional.of(original));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(outro));
        when(templateRepository.existsBySlug(anyString())).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.duplicateTemplate(1L, new DuplicateFormTemplateRequest(2L, "Cadastro Novo"));

        org.mockito.ArgumentCaptor<FormTemplate> captor = org.mockito.ArgumentCaptor.forClass(FormTemplate.class);
        verify(templateRepository).save(captor.capture());
        FormTemplate copy = captor.getValue();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getId()).isNull();
        assertThat(copy.getName()).isEqualTo("Cadastro Novo");
        assertThat(copy.getSlug()).isEqualTo("cadastro-novo");
        assertThat(copy.getClient()).isSameAs(outro);
        assertThat(copy.getViewToken()).isNull();

        // Toda configuração restante deve ser igual à do original. Se um campo
        // novo for criado no FormTemplate e esquecido no duplicateTemplate,
        // este teste falha (buildFullTemplate preenche tudo por reflexão).
        assertThat(copy).usingRecursiveComparison()
                .ignoringFields("id", "name", "slug", "client", "viewToken", "createdAt", "fields")
                .isEqualTo(original);

        assertThat(copy.getFields()).hasSize(2);
        assertThat(copy.getFields()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "formTemplate")
                .containsExactlyElementsOf(original.getFields());
        assertThat(copy.getFields()).allSatisfy(f -> {
            assertThat(f.getId()).isNull();
            assertThat(f.getFormTemplate()).isSameAs(copy);
        });

        verify(metricas).templateCriado();
    }

    @Test
    @DisplayName("duplicateTemplate: cópia não compartilha listas com o original")
    void duplicateTemplate_collectionsAreIndependent() throws Exception {
        FormTemplate original = buildFullTemplate();

        when(templateRepository.findById(1L)).thenReturn(Optional.of(original));
        when(templateRepository.existsBySlug(anyString())).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.duplicateTemplate(1L, new DuplicateFormTemplateRequest(null, null));

        org.mockito.ArgumentCaptor<FormTemplate> captor = org.mockito.ArgumentCaptor.forClass(FormTemplate.class);
        verify(templateRepository).save(captor.capture());
        FormTemplate copy = captor.getValue();

        copy.getFields().get(0).setLabel("Alterado");
        copy.getFields().get(0).getOptions().add("Nova opção");
        copy.getFields().add(new FormField());
        copy.getDedupFields().add("Outro");

        assertThat(original.getFields()).hasSize(2);
        assertThat(original.getFields().get(0).getLabel()).isEqualTo("Nome");
        assertThat(original.getFields().get(0).getOptions()).containsExactly("A", "B");
        assertThat(original.getDedupFields()).containsExactly("CPF");
    }

    @Test
    @DisplayName("duplicateTemplate: sem cliente e sem nome usa o cliente do original e '(cópia)'")
    void duplicateTemplate_defaults() throws Exception {
        FormTemplate original = buildFullTemplate();

        when(templateRepository.findById(1L)).thenReturn(Optional.of(original));
        when(templateRepository.existsBySlug("original-cpia")).thenReturn(true);
        when(templateRepository.existsBySlug("original-cpia-1")).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FormTemplateResponse response = service.duplicateTemplate(1L, new DuplicateFormTemplateRequest(null, "  "));

        assertThat(response.name()).isEqualTo("Original (cópia)");
        assertThat(response.slug()).isEqualTo("original-cpia-1");
        verify(clientRepository, never()).findById(any());
    }

    @Test
    @DisplayName("duplicateTemplate: template inexistente lança exceção e nada é salvo")
    void duplicateTemplate_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicateTemplate(99L, new DuplicateFormTemplateRequest(1L, "X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Template não encontrado");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("duplicateTemplate: cliente inexistente lança exceção e nada é salvo")
    void duplicateTemplate_clientNotFound_throws() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(buildTemplate("Original", "original")));
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicateTemplate(1L, new DuplicateFormTemplateRequest(99L, "X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cliente não encontrado");
        verify(templateRepository, never()).save(any());
    }

    // ─── updateTemplate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateTemplate: atualiza nome do template")
    void updateTemplate_updateName() {
        FormTemplate t = buildTemplate("Antigo", "antigo");
        UpdateFormTemplateRequest request = new UpdateFormTemplateRequest("Novo Nome", null, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            FormTemplate saved = inv.getArgument(0);
            assertThat(saved.getName()).isEqualTo("Novo Nome");
            return saved;
        });

        service.updateTemplate(1L, request);

        verify(templateRepository).save(t);
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private FormTemplate buildTemplate(String name, String slug) {
        return FormTemplate.builder()
                .id(1L)
                .name(name)
                .slug(slug)
                .client(client)
                .fields(new ArrayList<>())
                .hasSchedule(false)
                .hasAttendance(false)
                .build();
    }

    /**
     * Template com TODOS os atributos simples preenchidos com valores diferentes
     * do padrão (por reflexão), mais 2 campos, dedup, quiz e pesquisa.
     */
    private FormTemplate buildFullTemplate() throws Exception {
        FormTemplate t = new FormTemplate();
        for (java.lang.reflect.Field f : FormTemplate.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Class<?> type = f.getType();
            if (type == String.class) f.set(t, "v-" + f.getName());
            else if (type == boolean.class) f.set(t, !f.getBoolean(t));
            else if (type == int.class || type == Integer.class) f.set(t, 7);
            else if (type == LocalTime.class) f.set(t, LocalTime.of(9, 30));
        }
        t.setId(1L);
        t.setName("Original");
        t.setSlug("original");
        t.setViewToken("token-do-original");
        t.setClient(client);
        t.setDedupFields(new java.util.HashSet<>(Set.of("CPF")));
        t.setQuiz(com.cadastro.fabiano.demo.entity.QuizConfig.builder().id(5L).build());
        t.setSurvey(com.cadastro.fabiano.demo.entity.SurveyConfig.builder().id(6L).build());

        FormField nome = FormField.builder().id(10L).label("Nome").type("select").required(true)
                .fieldColor("#111").colSpan(1).options(new ArrayList<>(List.of("A", "B"))).formTemplate(t).build();
        FormField cpf = FormField.builder().id(11L).label("CPF").type("text").required(false)
                .colSpan(2).options(new ArrayList<>()).formTemplate(t).build();
        t.setFields(new ArrayList<>(List.of(nome, cpf)));
        return t;
    }
}
    