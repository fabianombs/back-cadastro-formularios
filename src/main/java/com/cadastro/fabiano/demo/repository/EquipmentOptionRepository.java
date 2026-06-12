package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.EquipmentOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipmentOptionRepository extends JpaRepository<EquipmentOption, Long> {

    // Busca paginada por trecho do nome (autocomplete). Mostra todas as opções.
    @Query("SELECT o FROM EquipmentOption o WHERE o.catalog.id = :catalogId " +
           "AND LOWER(o.label) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY o.label ASC")
    Page<EquipmentOption> search(@Param("catalogId") Long catalogId,
                                 @Param("q") String q,
                                 Pageable pageable);

    // Busca paginada apenas das opções com disponibilidade (estoque > 0).
    @Query("SELECT o FROM EquipmentOption o WHERE o.catalog.id = :catalogId " +
           "AND LOWER(o.label) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "AND o.totalQty - o.usedCount > 0 ORDER BY o.label ASC")
    Page<EquipmentOption> searchAvailable(@Param("catalogId") Long catalogId,
                                          @Param("q") String q,
                                          Pageable pageable);

    Optional<EquipmentOption> findByCatalogIdAndLabel(Long catalogId, String label);

    // Reserva atômica: só incrementa se ainda houver disponibilidade.
    // Retorna 1 se reservou, 0 se esgotado — evita atribuição em dobro em acessos simultâneos.
    @Modifying
    @Query("UPDATE EquipmentOption o SET o.usedCount = o.usedCount + 1 " +
           "WHERE o.id = :id AND o.usedCount < o.totalQty")
    int reserveOne(@Param("id") Long id);

    // Devolve uma unidade ao estoque, sem deixar negativo.
    @Modifying
    @Query("UPDATE EquipmentOption o SET o.usedCount = o.usedCount - 1 " +
           "WHERE o.id = :id AND o.usedCount > 0")
    int releaseOne(@Param("id") Long id);
}
