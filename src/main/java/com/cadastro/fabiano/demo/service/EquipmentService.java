package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.ImportEquipmentRequest;
import com.cadastro.fabiano.demo.dto.request.SelectEquipmentRequest;
import com.cadastro.fabiano.demo.dto.response.EquipmentCatalogResponse;
import com.cadastro.fabiano.demo.dto.response.EquipmentOptionResponse;
import com.cadastro.fabiano.demo.dto.response.EquipmentSelectionResponse;
import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.EquipmentCatalog;
import com.cadastro.fabiano.demo.entity.EquipmentOption;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.EquipmentCatalogRepository;
import com.cadastro.fabiano.demo.repository.EquipmentOptionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EquipmentService {

    private final EquipmentCatalogRepository catalogRepository;
    private final EquipmentOptionRepository optionRepository;
    private final FormTemplateRepository templateRepository;
    private final AttendanceRecordRepository attendanceRepository;

    public EquipmentService(EquipmentCatalogRepository catalogRepository,
                            EquipmentOptionRepository optionRepository,
                            FormTemplateRepository templateRepository,
                            AttendanceRecordRepository attendanceRepository) {
        this.catalogRepository = catalogRepository;
        this.optionRepository = optionRepository;
        this.templateRepository = templateRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Importa uma planilha de equipamentos como uma NOVA coluna de select na lista
     * de presenca. Cada import cria seu proprio catalogo: varias planilhas convivem
     * (ex. uma de celulares, outra de notebooks) e um novo import nunca sobrescreve
     * os anteriores. A columnKey e derivada do id para nao colidir no rowData.
     */
    @Transactional
    public EquipmentCatalogResponse importCatalog(Long templateId, ImportEquipmentRequest request) {
        FormTemplate template = findTemplate(templateId);
        String name = request.name() != null ? request.name().trim() : "Equipamentos";

        EquipmentCatalog catalog = EquipmentCatalog.builder()
                .formTemplate(template)
                .name(name)
                .columnKey(request.columnKey() != null && !request.columnKey().isBlank()
                        ? request.columnKey().trim() : null)
                .sourceColumn(request.sourceColumn())
                .stockControl(request.stockControl())
                .visible(request.visible())
                .build();
        catalogRepository.save(catalog);

        // columnKey estavel: se nao veio explicita, gera a partir do id (evita colisao no rowData)
        if (catalog.getColumnKey() == null || catalog.getColumnKey().isBlank()) {
            catalog.setColumnKey("equip_" + catalog.getId());
            catalogRepository.save(catalog);
        }

        final EquipmentCatalog saved = catalog;
        List<EquipmentOption> options = request.options().stream()
                .filter(o -> o.label() != null && !o.label().isBlank())
                .map(o -> EquipmentOption.builder()
                        .catalog(saved)
                        .label(o.label().trim())
                        // sem quantidade informada -> 0 (estoque ilimitado quando controle desligado)
                        .totalQty(o.quantity() == null ? 0 : Math.max(0, o.quantity()))
                        .usedCount(0)
                        .build())
                .toList();
        optionRepository.saveAll(options);

        return EquipmentCatalogResponse.from(saved, options.size());
    }

    /**
     * Remove a selecao de equipamento (coluna colKey) de todas as linhas da lista
     * de presenca do template. Usado ao remover (X) o catalogo: a coluna some junto
     * com os valores ja escolhidos pelos clientes.
     */
    private void clearSelections(FormTemplate template, String colKey) {
        if (colKey == null || colKey.isBlank()) {
            return;
        }
        for (AttendanceRecord rec : attendanceRepository.findByFormTemplateOrderByRowOrderAscCreatedAtAsc(template)) {
            Map<String, String> data = rec.getRowData();
            if (data != null && data.remove(colKey) != null) {
                rec.setRowData(data);
                attendanceRepository.save(rec);
            }
        }
    }

    // readOnly garante a sessao aberta para ler options (lazy) sem LazyInitializationException
    @Transactional(readOnly = true)
    public List<EquipmentCatalogResponse> listByTemplate(Long templateId) {
        FormTemplate template = findTemplate(templateId);
        return catalogRepository.findByFormTemplateOrderByCreatedAtAsc(template).stream()
                .map(c -> EquipmentCatalogResponse.from(c, c.getOptions().size()))
                .toList();
    }

    /**
     * Busca paginada de opcoes para o autocomplete.
     * onlyAvailable=true retorna apenas opcoes com estoque disponivel.
     */
    public Page<EquipmentOptionResponse> searchOptions(Long catalogId, String q, boolean onlyAvailable, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        Page<EquipmentOption> page = onlyAvailable
                ? optionRepository.searchAvailable(catalogId, term, pageable)
                : optionRepository.search(catalogId, term, pageable);
        return page.map(EquipmentOptionResponse::from);
    }

    @Transactional
    public EquipmentCatalogResponse setStockControl(Long catalogId, boolean enabled) {
        EquipmentCatalog catalog = findCatalog(catalogId);
        catalog.setStockControl(enabled);
        catalogRepository.save(catalog);
        return EquipmentCatalogResponse.from(catalog, catalog.getOptions().size());
    }

    @Transactional
    public EquipmentCatalogResponse setVisible(Long catalogId, boolean visible) {
        EquipmentCatalog catalog = findCatalog(catalogId);
        catalog.setVisible(visible);
        catalogRepository.save(catalog);
        return EquipmentCatalogResponse.from(catalog, catalog.getOptions().size());
    }

    @Transactional
    public void deleteCatalog(Long catalogId) {
        EquipmentCatalog catalog = findCatalog(catalogId);
        // X da planilha: remove a coluna e os valores ja escolhidos dessa lista de presenca
        clearSelections(catalog.getFormTemplate(), catalog.getColumnKey());
        catalogRepository.deleteById(catalogId);
    }

    /**
     * Seleciona (ou limpa) o equipamento de uma linha da lista de presenca.
     * Com controle de estoque ligado, faz a troca de forma atomica: devolve a
     * unidade da opcao anterior e reserva a nova. Se a nova estiver esgotada,
     * lanca erro e nada e alterado. O valor e gravado no rowData existente.
     */
    @Transactional
    public EquipmentSelectionResponse selectOption(SelectEquipmentRequest request) {
        EquipmentCatalog catalog = findCatalog(request.catalogId());
        AttendanceRecord record = attendanceRepository.findById(request.recordId())
                .orElseThrow(() -> new RuntimeException("Registro nao encontrado"));

        String columnKey = request.columnKey();
        String oldLabel = record.getRowData().get(columnKey);
        String newLabel = (request.label() == null || request.label().isBlank())
                ? null : request.label().trim();

        if (catalog.isStockControl() && !Objects.equals(oldLabel, newLabel)) {
            // Reserva a nova primeiro: se esgotou, aborta sem mexer no estoque antigo.
            if (newLabel != null) {
                EquipmentOption option = optionRepository
                        .findByCatalogIdAndLabel(catalog.getId(), newLabel)
                        .orElseThrow(() -> new RuntimeException("Equipamento nao encontrado: " + newLabel));
                if (optionRepository.reserveOne(option.getId()) == 0) {
                    throw new RuntimeException("Equipamento esgotado: " + newLabel);
                }
            }
            // Devolve a unidade da opcao anterior.
            if (oldLabel != null) {
                optionRepository.findByCatalogIdAndLabel(catalog.getId(), oldLabel)
                        .ifPresent(o -> optionRepository.releaseOne(o.getId()));
            }
        }

        Map<String, String> data = record.getRowData();
        if (newLabel == null) {
            data.remove(columnKey);
        } else {
            data.put(columnKey, newLabel);
        }
        record.setRowData(data);
        attendanceRepository.save(record);

        return new EquipmentSelectionResponse(record.getId(), columnKey, newLabel);
    }

    private FormTemplate findTemplate(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template nao encontrado"));
    }

    private EquipmentCatalog findCatalog(Long catalogId) {
        return catalogRepository.findById(catalogId)
                .orElseThrow(() -> new RuntimeException("Catalogo nao encontrado"));
    }
}
