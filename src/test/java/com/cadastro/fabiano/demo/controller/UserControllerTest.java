package com.cadastro.fabiano.demo.controller;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
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
