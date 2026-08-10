package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FABIANO-53 — created_at ficava NULL em todo cliente criado pela aplicacao.
 *
 * O defeito nao era falta de DEFAULT na tabela: a V2 declara
 * DEFAULT CURRENT_TIMESTAMP. Era o Hibernate INCLUIR a coluna no INSERT com
 * NULL explicito, e NULL explicito sobrepoe o DEFAULT do MySQL — o padrao so
 * vale quando a coluna e omitida do comando.
 *
 * Por isso este teste precisa de banco de verdade e de um INSERT de verdade:
 * um teste com repositorio mockado exercitaria o mock, nao o SQL gerado, e
 * passaria com ou sem a correcao. E o SQL gerado que era o bug.
 *
 * replace = NONE porque o @DataJpaTest trocaria a fonte de dados por uma
 * embarcada; e o MySQL do CI que precisa responder aqui.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @DisplayName("cliente novo nasce com created_at e updated_at preenchidos")
    void clienteNovoNasceComTimestamps() {
        LocalDateTime antes = LocalDateTime.now().minusMinutes(1);

        Client novo = Client.builder()
                // username e unique e not null; o nanoTime evita colisao entre
                // execucoes caso a transacao de teste nao seja desfeita.
                .username("teste_created_at_" + System.nanoTime())
                .name("Cliente de teste")
                .build();

        // saveAndFlush e nao save: o save adia o INSERT ate o fim da transacao,
        // e e o INSERT que este teste precisa observar.
        Client salvo = clientRepository.saveAndFlush(novo);

        assertThat(salvo.getCreatedAt())
                .as("created_at de cliente recem-criado")
                .isNotNull()
                .isAfter(antes);

        assertThat(salvo.getUpdatedAt())
                .as("updated_at de cliente recem-criado")
                .isNotNull()
                .isAfter(antes);
    }
}
