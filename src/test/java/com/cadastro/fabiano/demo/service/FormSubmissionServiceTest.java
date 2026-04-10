package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormSubmissionServiceTest {

    @Mock
    private FormSubmissionRepository submissionRepository;

    @Mock
    private FormTemplateRepository templateRepository;

    @InjectMocks
    private FormSubmissionService service;

    private FormTemplate template;

    @BeforeEach
    void setUp() {
        Client client = Client.builder().id(1L).name("Cliente").username("cliente").build();
        template = FormTemplate.builder()
                .id(1L)
                .name("Formulário")
                .slug("formulario")
                .client(client)
                .fields(new ArrayList<>())
                .build();
    }

    // ─── submitForm ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("submitForm: salva submissão e retorna resposta")
    void submitForm_success() {
        Map<String, String> values = Map.of("Nome", "João", "Email", "joao@email.com");
        CreateFormSubmissionRequest request = new CreateFormSubmissionRequest(1L, values);

        FormSubmission saved = FormSubmission.builder()
                .id(10L)
                .template(template)
                .values(values)
                .createdAt(LocalDateTime.now())
                .build();

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(submissionRepository.save(any())).thenReturn(saved);

        FormSubmissionResponse response = service.submitForm(request);

        assertThat(response.templateId()).isEqualTo(1L);
        assertThat(response.values()).containsKey("Nome");
        verify(submissionRepository).save(any());
    }

    @Test
    @DisplayName("submitForm: lança exceção se template não encontrado")
    void submitForm_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitForm(
                new CreateFormSubmissionRequest(99L, Map.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── getSubmissionsByTemplate ─────────────────────────────────────────────

    @Test
    @DisplayName("getSubmissionsByTemplate: retorna página de submissões")
    void getSubmissionsByTemplate_success() {
        FormSubmission s = FormSubmission.builder()
                .id(1L).template(template).values(Map.of()).createdAt(LocalDateTime.now()).build();
        PageRequest pageable = PageRequest.of(0, 10);

        when(submissionRepository.findByTemplate_Id(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(s)));

        Page<FormSubmissionResponse> result = service.getSubmissionsByTemplate(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── getSubmissionsBySlug ─────────────────────────────────────────────────

    @Test
    @DisplayName("getSubmissionsBySlug: retorna submissões pelo slug")
    void getSubmissionsBySlug_success() {
        FormSubmission s = FormSubmission.builder()
                .id(1L).template(template).values(Map.of()).createdAt(LocalDateTime.now()).build();
        PageRequest pageable = PageRequest.of(0, 10);

        when(templateRepository.findBySlug("formulario")).thenReturn(Optional.of(template));
        when(submissionRepository.findByTemplate_Id(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(s)));

        Page<FormSubmissionResponse> result = service.getSubmissionsBySlug("formulario", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getSubmissionsBySlug: lança exceção se slug não encontrado")
    void getSubmissionsBySlug_notFound_throws() {
        when(templateRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissionsBySlug("inexistente", PageRequest.of(0, 10)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── deleteSubmission ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteSubmission: exclui submissão com sucesso")
    void deleteSubmission_success() {
        FormSubmission s = FormSubmission.builder()
                .id(5L).template(template).values(Map.of()).build();

        when(submissionRepository.findById(5L)).thenReturn(Optional.of(s));

        service.deleteSubmission(5L);

        verify(submissionRepository).delete(s);
    }

    @Test
    @DisplayName("deleteSubmission: lança exceção se não encontrada")
    void deleteSubmission_notFound_throws() {
        when(submissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSubmission(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrada");
    }
}
