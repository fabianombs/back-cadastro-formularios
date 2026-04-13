package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.dto.request.LoginRequest;
import com.cadastro.fabiano.demo.dto.request.RegisterRequest;
import com.cadastro.fabiano.demo.dto.response.AuthResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository repository,
                       PasswordEncoder encoder,
                       JwtService jwtService) {

        this.repository = repository;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    /**
     * Registra um novo usuário administrativo.
     * <p>Valida que {@code password} e {@code confirmPassword} são iguais,
     * codifica a senha com BCrypt e atribui automaticamente a role {@code ROLE_ADMIN}.</p>
     *
     * @param request dados de registro (nome, e-mail, username, senha e confirmação)
     * @return {@link AuthResponse} contendo o token JWT gerado
     * @throws RuntimeException se as senhas não coincidirem
     */
    public AuthResponse register(RegisterRequest request) {

        if (!request.password().equals(request.confirmPassword())) {

            throw new RuntimeException("Passwords do not match");

        }

        User user = new User();

        user.setName(request.name());
        user.setEmail(request.email());
        user.setUsername(request.username());

        user.setPassword(encoder.encode(request.password()));

        user.setRole(Role.ROLE_FUNCIONARIO);

        repository.save(user);

        String token = jwtService.generateToken(user, user.getId());
        return new AuthResponse(token);

    }

    /**
     * Autentica um usuário existente.
     * <p>Busca o usuário pelo username, valida a senha com BCrypt e retorna o token JWT.</p>
     *
     * @param request credenciais (username e password)
     * @return {@link AuthResponse} contendo o token JWT gerado
     * @throws RuntimeException se o usuário não for encontrado ou a senha estiver incorreta
     */
    public AuthResponse login(LoginRequest request) {

        User user = repository.findByUsername(request.username())
                .orElseThrow();

        if (!encoder.matches(request.password(), user.getPassword())) {

            throw new RuntimeException("Invalid credentials");

        }
        String token = jwtService.generateToken(user, user.getId());

        return new AuthResponse(token);

    }

}