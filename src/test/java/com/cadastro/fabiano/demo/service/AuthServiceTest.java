package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.config.AuthService;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.dto.request.LoginRequest;
import com.cadastro.fabiano.demo.dto.request.RegisterRequest;
import com.cadastro.fabiano.demo.dto.response.AuthResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    // ─── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: cria usuário e retorna token JWT")
    void register_success() {
        RegisterRequest request = new RegisterRequest(
                "Fabiano", "fabiano@email.com", "fabiano", "senha123", "senha123");

        User savedUser = User.builder()
                .id(1L)
                .name("Fabiano")
                .username("fabiano")
                .role(Role.ROLE_ADMIN)
                .build();

        when(passwordEncoder.encode("senha123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        // getId() retorna null pois o AuthService não reatribui o resultado do save
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: lança exceção quando senhas não coincidem")
    void register_passwordMismatch_throws() {
        RegisterRequest request = new RegisterRequest(
                "Fabiano", "fabiano@email.com", "fabiano", "senha123", "diferente");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("match");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("register: atribui ROLE_FUNCIONARIO ao novo usuário")
    void register_assignsAdminRole() {
        RegisterRequest request = new RegisterRequest(
                "Admin", "admin@email.com", "admin", "pass", "pass");

        User savedUser = User.builder().id(2L).role(Role.ROLE_ADMIN).build();

        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            assertThat(u.getRole()).isEqualTo(Role.ROLE_FUNCIONARIO); // Verifica que a role é ROLE_FUNCIONARIO
            return savedUser;
        });
        when(jwtService.generateToken(any(), any())).thenReturn("token");

        authService.register(request);
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: autenticação bem-sucedida retorna token")
    void login_success() {
        User user = User.builder()
                .id(1L)
                .username("fabiano")
                .password("hash")
                .role(Role.ROLE_ADMIN)
                .build();

        LoginRequest request = new LoginRequest("fabiano", "senha123");

        when(userRepository.findByUsername("fabiano")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), eq(1L))).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("login: lança exceção com senha incorreta")
    void login_wrongPassword_throws() {
        User user = User.builder()
                .id(1L)
                .username("fabiano")
                .password("hash")
                .build();

        LoginRequest request = new LoginRequest("fabiano", "errada");

        when(userRepository.findByUsername("fabiano")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login: lança exceção quando usuário não encontrado")
    void login_userNotFound_throws() {
        LoginRequest request = new LoginRequest("naoexiste", "qualquer");

        when(userRepository.findByUsername("naoexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class);
    }
}
