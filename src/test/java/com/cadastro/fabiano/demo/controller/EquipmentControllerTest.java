package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ImportEquipmentRequest;
import com.cadastro.fabiano.demo.dto.request.SelectEquipmentRequest;
import com.cadastro.fabiano.demo.dto.response.EquipmentCatalogResponse;
import com.cadastro.fabiano.demo.dto.response.EquipmentOptionResponse;
import com.cadastro.fabiano.demo.dto.response.EquipmentSelectionResponse;
import com.cadastro.fabiano.demo.service.EquipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerTest {

    @Mock private EquipmentService service;
    @InjectMocks private EquipmentController controller;

    @Test
    void importCatalog_delegaEretorna200() {
        ImportEquipmentRequest req = new ImportEquipmentRequest("Celulares", "col", "Modelo", true, true, List.of());
        EquipmentCatalogResponse resp = new EquipmentCatalogResponse(1L, 1L, "Celulares", "col", "Modelo", true, true, 3);
        when(service.importCatalog(1L, req)).thenReturn(resp);

        ResponseEntity<EquipmentCatalogResponse> r = controller.importCatalog(1L, req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(resp);
    }

    @Test
    void listCatalogs_retornaLista() {
        when(service.listByTemplate(1L)).thenReturn(List.of());
        assertThat(controller.listCatalogs(1L).getBody()).isEmpty();
    }

    @Test
    void searchOptions_delega() {
        Page<EquipmentOptionResponse> page = new PageImpl<>(List.of(
                new EquipmentOptionResponse(1L, "iPhone 14", 150, 10, 140)));
        when(service.searchOptions(eq(10L), eq("ip"), eq(true), any())).thenReturn(page);

        ResponseEntity<Page<EquipmentOptionResponse>> r =
                controller.searchOptions(10L, "ip", true, PageRequest.of(0, 20));

        assertThat(r.getBody().getContent()).hasSize(1);
    }

    @Test
    void setStockControl_delega() {
        EquipmentCatalogResponse resp = new EquipmentCatalogResponse(1L, 1L, "C", "col", null, false, true, 0);
        when(service.setStockControl(10L, false)).thenReturn(resp);
        assertThat(controller.setStockControl(10L, false).getBody()).isEqualTo(resp);
    }

    @Test
    void setVisible_delega() {
        EquipmentCatalogResponse resp = new EquipmentCatalogResponse(1L, 1L, "C", "col", null, false, false, 0);
        when(service.setVisible(10L, false)).thenReturn(resp);
        assertThat(controller.setVisible(10L, false).getBody()).isEqualTo(resp);
    }

    @Test
    void select_delega() {
        SelectEquipmentRequest req = new SelectEquipmentRequest(5L, 10L, "col", "iPhone 14");
        EquipmentSelectionResponse resp = new EquipmentSelectionResponse(5L, "col", "iPhone 14");
        when(service.selectOption(req)).thenReturn(resp);
        assertThat(controller.select(req).getBody()).isEqualTo(resp);
    }

    @Test
    void deleteCatalog_retorna204() {
        ResponseEntity<Void> r = controller.deleteCatalog(10L);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteCatalog(10L);
    }
}
