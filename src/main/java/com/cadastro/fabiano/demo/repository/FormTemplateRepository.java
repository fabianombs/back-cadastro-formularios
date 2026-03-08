package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {
    List<FormTemplate> findByClient(User client);
}