package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.EquipmentCatalog;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentCatalogRepository extends JpaRepository<EquipmentCatalog, Long> {

    List<EquipmentCatalog> findByFormTemplateOrderByCreatedAtAsc(FormTemplate template);

    void deleteByFormTemplate(FormTemplate template);
}
