package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.dto.request.LoginRequest;
import com.cadastro.fabiano.demo.dto.request.RegisterRequest;
import com.cadastro.fabiano.demo.dto.response.AuthResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // O Micrometer usa ponto e o exportador do Prometheus traduz para
    // auth_login_total / auth_registro_total. Nomear com underline aqui
    // produziria auth_login_total_total.
    private static final String METRICA_LOGIN = "auth.login";
    private static final String METRICA_REGISTRO = "auth.registro";

    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final MeterRegistry metricas;

    public AuthService(UserRepository repository,
                       PasswordEncoder encoder,
                       JwtService jwtService,
                       MeterRegistry metricas) {

        this.repository = repository;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.metricas = metricas;
    }

    /**
     * O rotulo resultado tem um conjunto fechado de valores. Identificador de
     * pessoa - username, e-mail, CPF - nunca entra como rotulo: cada valor
     * distinto cria uma serie temporal propria, entao mil usuarios virariam mil
     * series e derrubariam o Prometheus. Alem de ser dado pessoal em ferramenta
     * de monitoramento. Quem precisa do identificador e o log, indexado pelo Loki.
     */
    private void contar(String metrica, String resultado) {
        metricas.counter(metrica, "resultado", resultado).increment();
    }

    /**
     * Registra um novo usuário administrativo.
     * <p>Valida que {@code password} e {@code confirmPassword} são iguais,
     * codifica a senha com BCrypt e atribui a role {@code ROLE_FUNCIONARIO}.</p>
     *
     * @param request dados de registro (nome, e-mail, username, senha e confirmação)
     * @return {@link AuthResponse} contendo o token JWT gerado
     * @throws RuntimeException se as senhas não coincidirem
     */
    public AuthResponse register(RegisterRequest request) {

        if (!request.password().equals(request.confirmPassword())) {

            contar(METRICA_REGISTRO, "falha_senhas_diferentes");
            throw new RuntimeException("Passwords do not match");

        }

        User user = new User();

        user.setName(request.name());
        user.setEmail(request.email());
        user.setUsername(request.username());

        user.setPassword(encoder.encode(request.password()));

        user.setRole(Role.ROLE_FUNCIONARIO);

        repository.save(user);

        contar(METRICA_REGISTRO, "sucesso");

        String token = jwtService.generateToken(user, user.getId());
        return new AuthResponse(token);

    }

    /**
     * Autentica um usuário existente.
     * <p>Busca o usuário pelo username, valida a senha com BCrypt e retorna o token JWT.</p>
     *
     * @param request credenciais (username e password)
     * @return {@link AuthResponse} contendo o token JWT gerado
     * @throws NoSuchElementException se o usuário não existir
     * @throws RuntimeException se a senha estiver incorreta
     */
    public AuthResponse login(LoginRequest request) {

        Optional<User> encontrado = repository.findByUsername(request.username());

        // O endpoint /auth/login e publico. Ate aqui, tentativa recusada nao
        // deixava rastro algum: forca bruta rodaria por dias sem sinal.
        if (encontrado.isEmpty()) {

            contar(METRICA_LOGIN, "falha_usuario_inexistente");
            log.warn("Login recusado: usuario inexistente. username={}", request.username());

            // Mesma excecao de antes (orElseThrow), para nao alterar a resposta
            // que o frontend ja trata.
            throw new NoSuchElementException();

        }

        User user = encontrado.get();

        if (!encoder.matches(request.password(), user.getPassword())) {

            contar(METRICA_LOGIN, "falha_credenciais");
            log.warn("Login recusado: senha invalida. username={}", request.username());

            throw new RuntimeException("Invalid credentials");

        }

        // ACHADO: este login nao verifica a flag active. Como a autenticacao e
        // feita a mao e nao pelo AuthenticationManager, o isEnabled() do
        // UserDetails nunca e consultado - usuario desativado entra normalmente.
        // Aqui o comportamento NAO foi alterado de proposito; a metrica torna o
        // problema visivel para que a correcao seja uma decisao deliberada.
        boolean inativo = Boolean.FALSE.equals(user.getActive());

        if (inativo) {
            contar(METRICA_LOGIN, "sucesso_usuario_inativo");
            log.warn("Login concedido a usuario INATIVO - a flag active nao e verificada. username={}",
                    request.username());
        } else {
            contar(METRICA_LOGIN, "sucesso");
        }

        String token = jwtService.generateToken(user, user.getId());

        return new AuthResponse(token);

    }

}
