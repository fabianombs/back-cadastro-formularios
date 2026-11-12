package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Story 1.4 — mover cadastros entre formularios.
 *
 * Precisa de banco de verdade (MySQL do CI, como o ClientRepositoryTest): o que
 * importa aqui e o SQL gerado pelo UPDATE em lote e pelo KEY() do mapa de
 * respostas. Com repositorio mockado o teste passaria mesmo com a consulta errada.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class FormSubmissionMoveRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private FormSubmissionRepository repository;

    private FormTemplate template(Client client, String marca) {
        return em.persist(FormTemplate.builder()
                .name("Form " + marca)
                .slug(marca)
                .client(client)
                .build());
    }

    private FormSubmission submission(FormTemplate t, Map<String, String> values) {
        return em.persist(FormSubmission.builder()
                .template(t)
                .values(new HashMap<>(values))
                .build());
    }

    @Test
    @DisplayName("moveAllToTemplate: muda só o formulário; id, respostas e createdAt ficam iguais")
    void moveAllPreservesIdValuesAndCreatedAt() {
        String marca = "move_" + System.nanoTime();
        Client client = em.persist(Client.builder()
                .username(marca).name("Cliente teste").email(marca + "@teste.local").build());
        FormTemplate origem = template(client, marca + "_origem");
        FormTemplate destino = template(client, marca + "_destino");

        FormSubmission a = submission(origem, Map.of("Nome", "Ana", "CPF", "111"));
        FormSubmission b = submission(origem, Map.of("Nome", "Bia", "Bairro", "Centro"));
        FormSubmission jaNoDestino = submission(destino, Map.of("Nome", "Caio"));
        em.flush();

        LocalDateTime criadoA = a.getCreatedAt();
        assertThat(criadoA).isNotNull();

        assertThat(repository.findIdsByTemplateId(origem.getId()))
                .containsExactly(a.getId(), b.getId());
        assertThat(repository.findDistinctLabelsByTemplateId(origem.getId()))
                .containsExactlyInAnyOrder("Nome", "CPF", "Bairro");

        int movidos = repository.moveAllToTemplate(origem.getId(), destino);

        assertThat(movidos).isEqualTo(2);
        assertThat(repository.countByTemplate_Id(origem.getId())).isZero();
        assertThat(repository.countByTemplate_Id(destino.getId())).isEqualTo(3);

        FormSubmission aDepois = repository.findById(a.getId()).orElseThrow();
        assertThat(aDepois.getTemplate().getId()).isEqualTo(destino.getId());
        assertThat(aDepois.getValues()).containsExactlyInAnyOrderEntriesOf(Map.of("Nome", "Ana", "CPF", "111"));
        // o MySQL pode cortar as frações de segundo ao gravar; o que importa é não virar "agora"
        assertThat(aDepois.getCreatedAt()).isCloseTo(criadoA, within(1, ChronoUnit.SECONDS));

        assertThat(repository.findById(jaNoDestino.getId()).orElseThrow().getTemplate().getId())
                .isEqualTo(destino.getId());
        assertThat(repository.findIdsByTemplateId(destino.getId()))
                .containsExactlyInAnyOrderElementsOf(List.of(a.getId(), b.getId(), jaNoDestino.getId()));
    }
}
