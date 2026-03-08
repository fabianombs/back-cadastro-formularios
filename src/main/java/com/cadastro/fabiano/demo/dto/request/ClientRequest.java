package com.cadastro.fabiano.demo.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(

        @NotBlank
        String name,

        @Email
        String email,

        String phone,

        String company,

        String notes,

        String username

) {}