package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {

    Page<FormTemplate> findByClient(Client client, Pageable pageable);

    Optional<FormTemplate> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByHasScheduleTrue();

    long countByHasScheduleFalseAndHasAttendanceTrue();

    long countByHasScheduleFalseAndHasAttendanceFalse();

    long countByClientAndHasScheduleTrue(Client client);

    long countByClientAndHasScheduleFalseAndHasAttendanceTrue(Client client);

    long countByClientAndHasScheduleFalseAndHasAttendanceFalse(Client client);
}
