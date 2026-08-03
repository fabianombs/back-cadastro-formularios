package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.config.AuthService;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.dto.request.LoginRequest;
import com.cadastro.fabiano.demo.dto.request.RegisterRequest;
import com.cadastro.fabiano.demo.dto.response.AuthResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    // Registry de verdade, nao mock: o contador precisa de um registry
    // funcional, e mock devolveria null no register(). O SimpleMeterRegistry
    // guarda os valores em memoria, o que permite asserir a contagem.
    private final MeterRegistry metricas = new SimpleMeterRegistry();

    private AuthService authService;

    @BeforeEach
    void montarServico() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, metricas);
    }

    /** Le o valor atual de auth.login para um dado resultado. */
    private double contagemLogin(String resultado) {
        var c = metricas.find("auth.login").tag("resultado", resultado).counter();
        return c == null ? 0d : c.count();
    }

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

    // ─── métricas (FABIANO-24) ────────────────────────────────────────────────

    @Test
    @DisplayName("métricas: login bem-sucedido incrementa resultado=sucesso")
    void metrica_loginSucesso() {
        User user = User.builder()
                .id(1L).username("fabiano").password("hash").active(true)
                .role(Role.ROLE_ADMIN).build();

        when(userRepository.findByUsername("fabiano")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), eq(1L))).thenReturn("jwt-token");

        authService.login(new LoginRequest("fabiano", "senha123"));

        assertThat(contagemLogin("sucesso")).isEqualTo(1d);
        assertThat(contagemLogin("falha_credenciais")).isZero();
    }

    @Test
    @DisplayName("métricas: senha errada incrementa resultado=falha_credenciais")
    void metrica_loginSenhaErrada() {
        User user = User.builder().id(1L).username("fabiano").password("hash").build();

        when(userRepository.findByUsername("fabiano")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("fabiano", "errada")))
                .isInstanceOf(RuntimeException.class);

        assertThat(contagemLogin("falha_credenciais")).isEqualTo(1d);
        assertThat(contagemLogin("sucesso")).isZero();
    }

    @Test
    @DisplayName("métricas: usuário inexistente é contado separado da senha errada")
    void metrica_loginUsuarioInexistente() {
        when(userRepository.findByUsername("ninguem")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem", "x")))
                .isInstanceOf(RuntimeException.class);

        // Separar os dois motivos importa: rajada de usuario_inexistente e
        // varredura de nomes; rajada de falha_credenciais e forca bruta em
        // conta conhecida. O alerta e diferente para cada um.
        assertThat(contagemLogin("falha_usuario_inexistente")).isEqualTo(1d);
        assertThat(contagemLogin("falha_credenciais")).isZero();
    }

    // ─── usuário inativo (FABIANO-35) ─────────────────────────────────────────

    @Test
    @DisplayName("login: usuário inativo é recusado mesmo com a senha correta")
    void login_usuarioInativo_recusado() {
        // A senha esta CERTA de proposito: e o unico jeito de provar que a
        // recusa veio da flag active e nao da credencial.
        User inativo = User.builder()
                .id(9L).username("desligado").password("hash").active(false)
                .role(Role.ROLE_ADMIN).build();

        when(userRepository.findByUsername("desligado")).thenReturn(Optional.of(inativo));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("desligado", "senha")))
                .isInstanceOf(RuntimeException.class)
                // Mesma mensagem da senha errada: de fora nao da para descobrir
                // que o usuario existe e esta desligado.
                .hasMessage("Invalid credentials");

        // Nenhum token pode ter sido emitido.
        verifyNoInteractions(jwtService);

        assertThat(contagemLogin("falha_usuario_inativo")).isEqualTo(1d);
        assertThat(contagemLogin("sucesso")).isZero();
        assertThat(contagemLogin("sucesso_usuario_inativo")).isZero();
    }

    @Test
    @DisplayName("login: usuário ativo continua entrando normalmente")
    void login_usuarioAtivo_entra() {
        User ativo = User.builder()
                .id(1L).username("fabiano").password("hash").active(true)
                .role(Role.ROLE_ADMIN).build();

        when(userRepository.findByUsername("fabiano")).thenReturn(Optional.of(ativo));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), eq(1L))).thenReturn("jwt-token");

        assertThat(authService.login(new LoginRequest("fabiano", "senha123")).token())
                .isEqualTo("jwt-token");

        assertThat(contagemLogin("sucesso")).isEqualTo(1d);
        assertThat(contagemLogin("falha_usuario_inativo")).isZero();
    }

    @Test
    @DisplayName("login: active nulo (linha antiga do banco) é tratado como ativo")
    void login_activeNulo_entra() {
        // A coluna nasceu como "active BOOLEAN DEFAULT TRUE" (V1), aceitando
        // nulo. Se nulo fosse lido como inativo, a correcao derrubaria usuarios
        // antigos que nunca foram desligados por ninguem.
        User semFlag = User.builder()
                .id(7L).username("antigo").password("hash").active(null)
                .role(Role.ROLE_FUNCIONARIO).build();

        when(userRepository.findByUsername("antigo")).thenReturn(Optional.of(semFlag));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), eq(7L))).thenReturn("jwt-token");

        assertThat(authService.login(new LoginRequest("antigo", "senha")).token())
                .isEqualTo("jwt-token");

        assertThat(contagemLogin("sucesso")).isEqualTo(1d);
        assertThat(contagemLogin("falha_usuario_inativo")).isZero();
    }
}
