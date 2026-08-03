package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.dto.request.UpdateUserRequest;
import com.cadastro.fabiano.demo.dto.response.UserResponse;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = UserController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class UserControllerTest {

    // O GlobalExceptionHandler e @RestControllerAdvice e entra na fatia
    // do @WebMvcTest. Desde o FABIANO-25 ele depende de MetricasDeNegocio:
    // sem este bean o contexto do teste nem sobe.
    @MockitoBean
    private MetricasDeNegocio metricasDeNegocio;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private UserResponse buildUser(Long id) {
        return new UserResponse(id, "Fabiano", "fabiano@email.com", "fabiano", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("GET /users: lista todos os usuários paginados")
    void findAll_success() throws Exception {
        when(userService.findAll(any())).thenReturn(new PageImpl<>(List.of(buildUser(1L))));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/clients: lista usuários CLIENT paginados")
    void findClients_success() throws Exception {
        when(userService.findByRole(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users/clients"))
                .andExpect(status().isOk());
    }

    /**
     * Contrato do JSON de pagina (FABIANO-36).
     *
     * O front Angular le exatamente estes sete campos. Trocar PageImpl por
     * PaginaResponse so e seguro enquanto o JSON continuar identico - este teste
     * e o que garante isso, e vai quebrar se alguem mexer no envelope.
     */
    @Test
    @DisplayName("GET /users: o JSON de página tem os 7 campos que o front lê, e só eles")
    void findAll_contratoDoJsonDePagina() throws Exception {
        // 87 elementos em paginas de 20, posicionado na pagina 1: assim nenhum
        // campo cai em valor default e um erro de mapeamento fica visivel.
        when(userService.findAll(any())).thenReturn(
                new PageImpl<>(List.of(buildUser(1L)), PageRequest.of(1, 20), 87));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(87))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false))
                // Campos que o PageImpl emitia e ninguem consumia. Se voltarem,
                // e porque alguem devolveu Page direto de novo.
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist())
                .andExpect(jsonPath("$.numberOfElements").doesNotExist())
                .andExpect(jsonPath("$.empty").doesNotExist());
    }

    @Test
    @DisplayName("PUT /users/{id}: atualiza usuário")
    void update_success() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("Novo Nome", "novo@email.com", "ROLE_FUNCIONARIO");
        doNothing().when(userService).update(eq(1L), any());

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /users/{id}: exclui usuário")
    void delete_success() throws Exception {
        doNothing().when(userService).delete(1L);

        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isOk());
    }
}
