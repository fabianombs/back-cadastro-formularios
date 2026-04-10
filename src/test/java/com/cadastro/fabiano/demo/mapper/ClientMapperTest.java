package com.cadastro.fabiano.demo.mapper;

import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMapperTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private ClientRequest buildRequest() {
        return new ClientRequest("Empresa XYZ", "xyz@email.com", "11999990000", "Empresa", "Notas", "empresa_xyz");
    }

    @Test
    @DisplayName("toEntity: mapeia ClientRequest para Client sem User")
    void toEntity_mapsCorrectly() {
        Client client = ClientMapper.toEntity(buildRequest());

        assertThat(client.getName()).isEqualTo("Empresa XYZ");
        assertThat(client.getEmail()).isEqualTo("xyz@email.com");
        assertThat(client.getPhone()).isEqualTo("11999990000");
        assertThat(client.getCompany()).isEqualTo("Empresa");
        assertThat(client.getNotes()).isEqualTo("Notas");
        assertThat(client.getUser()).isNull();
    }

    @Test
    @DisplayName("toDTO: mapeia Client para ClientResponse")
    void toDTO_mapsCorrectly() {
        Client client = Client.builder()
                .id(1L)
                .name("Empresa XYZ")
                .email("xyz@email.com")
                .phone("11999990000")
                .company("Empresa")
                .notes("Notas")
                .build();

        ClientResponse response = ClientMapper.toDTO(client);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Empresa XYZ");
        assertThat(response.email()).isEqualTo("xyz@email.com");
    }

    @Test
    @DisplayName("toEntityWithUser: cria Client com User associado")
    void toEntityWithUser_createsUserWithRole() {
        Client client = ClientMapper.toEntityWithUser(buildRequest(), encoder);

        assertThat(client.getName()).isEqualTo("Empresa XYZ");
        assertThat(client.getUsername()).isEqualTo("empresa_xyz");
        assertThat(client.getUser()).isNotNull();
        assertThat(client.getUser().getRole()).isEqualTo(Role.ROLE_CLIENT);
        assertThat(client.getUser().getActive()).isTrue();
        assertThat(encoder.matches("123456", client.getUser().getPassword())).isTrue();
    }
}
