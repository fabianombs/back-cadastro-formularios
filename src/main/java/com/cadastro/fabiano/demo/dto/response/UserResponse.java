package com.cadastro.fabiano.demo.dto.response;

public record UserResponse(

        Long id,
        String name,
        String email,
        String username,
        String role

) {}