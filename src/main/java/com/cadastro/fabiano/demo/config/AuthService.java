package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.dto.request.LoginRequest;
import com.cadastro.fabiano.demo.dto.request.RegisterRequest;
import com.cadastro.fabiano.demo.dto.response.AuthResponse;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);

    }

    public AuthResponse login(LoginRequest request) {

        User user = repository.findByUsername(request.username())
                .orElseThrow();

        if (!encoder.matches(request.password(), user.getPassword())) {

            throw new RuntimeException("Invalid credentials");

        }
        String token = jwtService.generateToken(user);

        return new AuthResponse(token);

    }

}