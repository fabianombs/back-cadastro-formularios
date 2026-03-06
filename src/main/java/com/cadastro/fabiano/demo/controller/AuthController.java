package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.AuthService;
import com.cadastro.fabiano.demo.dto.request.LoginRequest;
import com.cadastro.fabiano.demo.dto.request.RegisterRequest;
import com.cadastro.fabiano.demo.dto.response.AuthResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {

        return service.register(request);

    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {

        return service.login(request);

    }

}