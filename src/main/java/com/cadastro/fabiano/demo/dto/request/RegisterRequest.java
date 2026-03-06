package com.cadastro.fabiano.demo.dto.request;

public record RegisterRequest(

        String name,
        String email,
        String username,
        String password,
        String confirmPassword

) {}
