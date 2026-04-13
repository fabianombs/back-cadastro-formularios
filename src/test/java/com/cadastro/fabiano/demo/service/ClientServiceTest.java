package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.ClientRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FormTemplateService formTemplateService;

    @InjectMocks
    private ClientService clientService;

    private Client client;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L)
                .name("Empresa XYZ")
                .email("xyz@empresa.com")
                .username("empresa_xyz")
                .templates(new ArrayList<>())
                .build();
    }

    // ─── findAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll: retorna página de clientes")
    void findAll_success() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(client)));

        Page<ClientResponse> result = clientService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Empresa XYZ");
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById: retorna cliente quando encontrado")
    void findById_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(client));

        ClientResponse result = clientService.findById(1L);

        assertThat(result.name()).isEqualTo("Empresa XYZ");
    }

    @Test
    @DisplayName("findById: lança exceção quando não encontrado")
    void findById_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: realiza soft delete do cliente sem templates")
    void delete_success_noTemplates() {
        when(repository.findById(1L)).thenReturn(Optional.of(client));
        when(repository.save(any(Client.class))).thenReturn(client);

        clientService.delete(1L);

        assertThat(client.isDeleted()).isTrue();
        verify(repository).save(client);
        verifyNoInteractions(formTemplateService);
    }

    @Test
    @DisplayName("delete: realiza soft delete de cliente com templates e deleta cada template")
    void delete_success_withTemplates() {
        FormTemplate t1 = FormTemplate.builder().id(10L).name("Form 1").slug("form-1").build();
        FormTemplate t2 = FormTemplate.builder().id(11L).name("Form 2").slug("form-2").build();
        client.setTemplates(List.of(t1, t2));

        when(repository.findById(1L)).thenReturn(Optional.of(client));
        when(repository.save(any(Client.class))).thenReturn(client);

        clientService.delete(1L);

        assertThat(client.isDeleted()).isTrue();
        verify(formTemplateService).deleteTemplate(10L);
        verify(formTemplateService).deleteTemplate(11L);
    }

    @Test
    @DisplayName("delete: lança exceção quando cliente não encontrado")
    void delete_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.delete(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
