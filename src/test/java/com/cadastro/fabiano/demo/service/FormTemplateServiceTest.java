package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.FormFieldRequest;
import com.cadastro.fabiano.demo.dto.request.ScheduleConfigRequest;
import com.cadastro.fabiano.demo.dto.request.UpdateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormField;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.ClientRepository;
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
    private UserRepository userRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ImageStorageService imageStorageService;

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
    }

    // ─── createTemplate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createTemplate: cria template sem agenda")
    void createTemplate_success_noSchedule() {
        FormFieldRequest field = new FormFieldRequest("Nome", "text", true, null, 2, null);
        CreateFormTemplateRequest request = new CreateFormTemplateRequest(
                "Formulário de Contato", 1L, List.of(field), null, null);

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
                "Agenda Médica", 1L, List.of(), sc, null);

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
                "Formulario", 1L, List.of(), null, null);

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
                "Form", 99L, List.of(), null, null);

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
    @DisplayName("deleteTemplate: realiza soft delete")
    void deleteTemplate_success() {
        FormTemplate t = buildTemplate("Form Deletado", "form-deletado");

        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenReturn(t);
        // template não possui URLs de imagem, logo tryDeleteOrphanedImageExcluding retorna cedo
        // sem precisar consultar o repositório

        service.deleteTemplate(1L);

        assertThat(t.isDeleted()).isTrue();
        verify(templateRepository).save(t);
    }

    @Test
    @DisplayName("deleteTemplate: lança exceção se template não encontrado")
    void deleteTemplate_notFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTemplate(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── updateTemplate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateTemplate: atualiza nome do template")
    void updateTemplate_updateName() {
        FormTemplate t = buildTemplate("Antigo", "antigo");
        UpdateFormTemplateRequest request = new UpdateFormTemplateRequest("Novo Nome", null, null);

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
}
