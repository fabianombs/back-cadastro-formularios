package com.cadastro.fabiano.demo.mapper;


import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.Role;
import com.cadastro.fabiano.demo.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;

public class ClientMapper {

    public static Client toEntity(ClientRequest dto){

        return Client.builder()
                .name(dto.name())
                .email(dto.email())
                .phone(dto.phone())
                .company(dto.company())
                .notes(dto.notes())
                .build();

    }

    public static ClientResponse toDTO(Client client){

        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getCompany(),
                client.getNotes()
        );

    }

    public static Client toEntityWithUser(ClientRequest dto, PasswordEncoder passwordEncoder) {

        // cria User automático
        User user = User.builder()
                .username(dto.username())  // assume que o DTO tem username
                .password(passwordEncoder.encode("123456")) // senha default
                .role(Role.ROLE_CLIENT)
                .active(true)
                .name(dto.name())
                .email(dto.email())
                .build();

        // cria Client e associa o User
        return Client.builder()
                .name(dto.name())
                .email(dto.email())
                .phone(dto.phone())
                .company(dto.company())
                .notes(dto.notes())
                .username(dto.username())
                .user(user)
                .build();
    }

}