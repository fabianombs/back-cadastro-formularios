package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.QuizConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {

    Page<FormTemplate> findByClient(Client client, Pageable pageable);

    Optional<FormTemplate> findBySlug(String slug);

    /**
     * Busca o template com PESSIMISTIC_WRITE lock para serializar bookings concorrentes.
     * Garante que apenas um agendamento por slot seja processado por vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM FormTemplate t WHERE t.id = :id")
    Optional<FormTemplate> findByIdWithLock(@Param("id") Long id);

    boolean existsBySlug(String slug);

    /**
     * Conta quantos templates ativos (não deletados) referenciam a URL de imagem informada
     * em qualquer um dos campos de imagem. Usado para decidir se o arquivo pode ser removido
     * do disco com segurança após uma edição ou exclusão de template.
     */
    @Query("SELECT COUNT(t) FROM FormTemplate t WHERE t.headerImageUrl = :url OR t.footerImageUrl = :url OR t.backgroundImageUrl = :url")
    long countUsingImageUrl(@Param("url") String url);

    /**
     * Mesmo que {@link #countUsingImageUrl} mas exclui um template específico pelo ID.
     * Usado durante soft-delete para não depender do flush do @SQLRestriction na mesma transação.
     */
    @Query("SELECT COUNT(t) FROM FormTemplate t WHERE (t.headerImageUrl = :url OR t.footerImageUrl = :url OR t.backgroundImageUrl = :url) AND t.id <> :excludeId")
    long countUsingImageUrlExcluding(@Param("url") String url, @Param("excludeId") Long excludeId);

    long countByHasScheduleTrue();

    long countByHasScheduleFalseAndHasAttendanceTrue();

    long countByHasScheduleFalseAndHasAttendanceFalse();

    long countByClientAndHasScheduleTrue(Client client);

    long countByClientAndHasScheduleFalseAndHasAttendanceTrue(Client client);

    long countByClientAndHasScheduleFalseAndHasAttendanceFalse(Client client);

    // Verifica se algum template está usando determinado quiz (para impedir exclusão)
    boolean existsByQuiz(QuizConfig quiz);
}
