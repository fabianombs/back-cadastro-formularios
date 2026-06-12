package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.ImportEquipmentRequest;
import com.cadastro.fabiano.demo.dto.request.ImportEquipmentRequest.OptionInput;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock private EquipmentCatalogRepository catalogRepository;
    @Mock private EquipmentOptionRepository optionRepository;
    @Mock private FormTemplateRepository templateRepository;
    @Mock private AttendanceRecordRepository attendanceRepository;

    @InjectMocks private EquipmentService service;

    private FormTemplate template;

    @BeforeEach
    void setUp() {
        template = FormTemplate.builder().id(1L).name("Evento").slug("evento").build();
    }

    private EquipmentCatalog catalog(Long id, boolean stock) {
        return EquipmentCatalog.builder()
                .id(id).formTemplate(template).name("Celulares").columnKey("col_aparelho")
                .stockControl(stock).visible(true).build();
    }

    // import

    @Test
    @DisplayName("importCatalog: cria catalogo, ignora labels vazios e trata quantidade nula como 0")
    void importCatalog_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        ImportEquipmentRequest req = new ImportEquipmentRequest(
                "Celulares", "col_aparelho", "Modelo", true, true,
                List.of(new OptionInput("iPhone 14", 150),
                        new OptionInput("  ", 5),
                        new OptionInput("Galaxy S24", null)
                ));

        EquipmentCatalogResponse resp = service.importCatalog(1L, req);

        assertThat(resp.name()).isEqualTo("Celulares");
        assertThat(resp.stockControl()).isTrue();
        assertThat(resp.visible()).isTrue();
        assertThat(resp.optionsCount()).isEqualTo(2);
        assertThat(resp.templateId()).isEqualTo(1L);
        verify(catalogRepository, atLeastOnce()).save(any(EquipmentCatalog.class));
        verify(optionRepository).saveAll(any());
    }

    @Test
    @DisplayName("importCatalog: usa nome padrao e gera columnKey quando nao informados")
    void importCatalog_defaults() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        ImportEquipmentRequest req = new ImportEquipmentRequest(
                null, null, null, false, true, List.of(new OptionInput("A", 1)));

        EquipmentCatalogResponse resp = service.importCatalog(1L, req);

        assertThat(resp.name()).isEqualTo("Equipamentos");
        assertThat(resp.stockControl()).isFalse();
    }

    @Test
    @DisplayName("importCatalog: lanca erro quando template nao existe")
    void importCatalog_templateNotFound() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());
        ImportEquipmentRequest req = new ImportEquipmentRequest("x", null, "y", false, true, List.of());
        assertThatThrownBy(() -> service.importCatalog(99L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Template");
    }

    // listByTemplate

    @Test
    @DisplayName("listByTemplate: mapeia catalogos com contagem de opcoes")
    void listByTemplate_success() {
        EquipmentCatalog c = catalog(10L, false);
        c.setOptions(List.of(
                EquipmentOption.builder().id(1L).label("A").build(),
                EquipmentOption.builder().id(2L).label("B").build()));
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(catalogRepository.findByFormTemplateOrderByCreatedAtAsc(template)).thenReturn(List.of(c));

        List<EquipmentCatalogResponse> list = service.listByTemplate(1L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).optionsCount()).isEqualTo(2);
        assertThat(list.get(0).columnKey()).isEqualTo("col_aparelho");
    }

    // searchOptions

    @Test
    @DisplayName("searchOptions: onlyAvailable=true usa a query de disponiveis")
    void searchOptions_onlyAvailable() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<EquipmentOption> page = new PageImpl<>(List.of(
                EquipmentOption.builder().id(1L).label("iPhone 14").totalQty(150).usedCount(10).build()));
        when(optionRepository.searchAvailable(eq(10L), eq("iphone"), any(Pageable.class))).thenReturn(page);

        Page<EquipmentOptionResponse> result = service.searchOptions(10L, "iphone", true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).available()).isEqualTo(140);
        verify(optionRepository, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("searchOptions: onlyAvailable=false usa a query geral e trata q nulo")
    void searchOptions_all_nullQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(optionRepository.search(eq(10L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<EquipmentOptionResponse> result = service.searchOptions(10L, null, false, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(optionRepository, never()).searchAvailable(any(), any(), any());
    }

    // toggles / delete

    @Test
    @DisplayName("setStockControl: atualiza o flag e salva")
    void setStockControl_success() {
        EquipmentCatalog c = catalog(10L, false);
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(c));

        EquipmentCatalogResponse resp = service.setStockControl(10L, true);

        assertThat(resp.stockControl()).isTrue();
        assertThat(c.isStockControl()).isTrue();
        verify(catalogRepository).save(c);
    }

    @Test
    @DisplayName("setVisible: atualiza o flag de visibilidade e salva")
    void setVisible_success() {
        EquipmentCatalog c = catalog(10L, false);
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(c));

        EquipmentCatalogResponse resp = service.setVisible(10L, false);

        assertThat(resp.visible()).isFalse();
        assertThat(c.isVisible()).isFalse();
        verify(catalogRepository).save(c);
    }

    @Test
    @DisplayName("deleteCatalog: remove quando existe")
    void deleteCatalog_success() {
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(catalog(10L, false)));
        service.deleteCatalog(10L);
        verify(catalogRepository).deleteById(10L);
    }

    @Test
    @DisplayName("deleteCatalog: lanca erro quando catalogo nao existe")
    void deleteCatalog_notFound() {
        when(catalogRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteCatalog(404L))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Catalogo");
    }

    // selectOption

    private AttendanceRecord record(Long id, Map<String, String> data) {
        return AttendanceRecord.builder().id(id).formTemplate(template).rowData(data).build();
    }

    @Test
    @DisplayName("selectOption: sem estoque apenas grava no rowData (nao mexe em reserva)")
    void selectOption_noStock() {
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(catalog(10L, false)));
        AttendanceRecord rec = record(5L, new HashMap<>());
        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(rec));

        EquipmentSelectionResponse resp = service.selectOption(
                new SelectEquipmentRequest(5L, 10L, "col_aparelho", "iPhone 14"));

        assertThat(resp.label()).isEqualTo("iPhone 14");
        assertThat(rec.getRowData().get("col_aparelho")).isEqualTo("iPhone 14");
        verify(optionRepository, never()).reserveOne(any());
        verify(attendanceRepository).save(rec);
    }

    @Test
    @DisplayName("selectOption: com estoque reserva a nova e devolve a anterior")
    void selectOption_stock_swap() {
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(catalog(10L, true)));
        Map<String, String> data = new HashMap<>();
        data.put("col_aparelho", "iPhone 13");
        AttendanceRecord rec = record(5L, data);
        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(rec));
        when(optionRepository.findByCatalogIdAndLabel(10L, "iPhone 14"))
                .thenReturn(Optional.of(EquipmentOption.builder().id(1L).label("iPhone 14").totalQty(2).usedCount(0).build()));
        when(optionRepository.reserveOne(1L)).thenReturn(1);
        when(optionRepository.findByCatalogIdAndLabel(10L, "iPhone 13"))
                .thenReturn(Optional.of(EquipmentOption.builder().id(2L).label("iPhone 13").totalQty(2).usedCount(1).build()));

        EquipmentSelectionResponse resp = service.selectOption(
                new SelectEquipmentRequest(5L, 10L, "col_aparelho", "iPhone 14"));

        assertThat(resp.label()).isEqualTo("iPhone 14");
        verify(optionRepository).reserveOne(1L);
        verify(optionRepository).releaseOne(2L);
        assertThat(rec.getRowData().get("col_aparelho")).isEqualTo("iPhone 14");
    }

    @Test
    @DisplayName("selectOption: equipamento esgotado lanca erro e nao grava")
    void selectOption_stock_exhausted() {
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(catalog(10L, true)));
        AttendanceRecord rec = record(5L, new HashMap<>());
        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(rec));
        when(optionRepository.findByCatalogIdAndLabel(10L, "iPhone 14"))
                .thenReturn(Optional.of(EquipmentOption.builder().id(1L).label("iPhone 14").totalQty(1).usedCount(1).build()));
        when(optionRepository.reserveOne(1L)).thenReturn(0);

        assertThatThrownBy(() -> service.selectOption(
                new SelectEquipmentRequest(5L, 10L, "col_aparelho", "iPhone 14")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("esgotado");
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("selectOption: label vazio limpa a selecao e devolve estoque")
    void selectOption_clear() {
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(catalog(10L, true)));
        Map<String, String> data = new HashMap<>();
        data.put("col_aparelho", "iPhone 13");
        AttendanceRecord rec = record(5L, data);
        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(rec));
        when(optionRepository.findByCatalogIdAndLabel(10L, "iPhone 13"))
                .thenReturn(Optional.of(EquipmentOption.builder().id(2L).label("iPhone 13").totalQty(2).usedCount(1).build()));

        EquipmentSelectionResponse resp = service.selectOption(
                new SelectEquipmentRequest(5L, 10L, "col_aparelho", "  "));

        assertThat(resp.label()).isNull();
        assertThat(rec.getRowData()).doesNotContainKey("col_aparelho");
        verify(optionRepository).releaseOne(2L);
        verify(optionRepository, never()).reserveOne(any());
    }

    @Test
    @DisplayName("selectOption: registro inexistente lanca erro")
    void selectOption_recordNotFound() {
        when(catalogRepository.findById(10L)).thenReturn(Optional.of(catalog(10L, false)));
        when(attendanceRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.selectOption(
                new SelectEquipmentRequest(5L, 10L, "col", "x")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Registro");
    }
}
