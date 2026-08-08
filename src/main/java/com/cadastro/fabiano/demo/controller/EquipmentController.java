package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ImportEquipmentRequest;
import com.cadastro.fabiano.demo.dto.request.SelectEquipmentRequest;
import com.cadastro.fabiano.demo.dto.response.EquipmentCatalogResponse;
import com.cadastro.fabiano.demo.dto.response.EquipmentOptionResponse;
import com.cadastro.fabiano.demo.dto.response.EquipmentSelectionResponse;
import com.cadastro.fabiano.demo.dto.response.PaginaResponse;
import com.cadastro.fabiano.demo.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/equipment")
@Tag(name = "Equipamentos", description = "Catalogo de equipamentos (2a planilha) para selecao na lista de presenca")
@SecurityRequirements
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @PostMapping("/template/{templateId}/import")
    @Operation(summary = "Importar catalogo de equipamentos",
            description = "Cria um catalogo a partir dos valores distintos (label + quantidade) extraidos da 2a planilha")
    @ApiResponse(responseCode = "200", description = "Catalogo importado")
    public ResponseEntity<EquipmentCatalogResponse> importCatalog(
            @PathVariable Long templateId,
            @RequestBody ImportEquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.importCatalog(templateId, request));
    }

    @GetMapping("/template/{templateId}/catalogs")
    @Operation(summary = "Listar catalogos do template")
    public ResponseEntity<List<EquipmentCatalogResponse>> listCatalogs(@PathVariable Long templateId) {
        return ResponseEntity.ok(equipmentService.listByTemplate(templateId));
    }

    @GetMapping("/catalog/{catalogId}/options")
    @Operation(summary = "Buscar opcoes (autocomplete paginado)",
            description = "Filtra por trecho do nome. onlyAvailable=true retorna apenas opcoes com estoque disponivel")
    public ResponseEntity<PaginaResponse<EquipmentOptionResponse>> searchOptions(
            @PathVariable Long catalogId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean onlyAvailable,
            Pageable pageable) {
        return ResponseEntity.ok(PaginaResponse.de(
                equipmentService.searchOptions(catalogId, q, onlyAvailable, pageable)));
    }

    @PatchMapping("/catalog/{catalogId}/stock-control")
    @Operation(summary = "Ligar/desligar controle de estoque do catalogo")
    public ResponseEntity<EquipmentCatalogResponse> setStockControl(
            @PathVariable Long catalogId,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(equipmentService.setStockControl(catalogId, enabled));
    }

    @PatchMapping("/catalog/{catalogId}/visible")
    @Operation(summary = "Mostrar/ocultar a coluna do catalogo")
    public ResponseEntity<EquipmentCatalogResponse> setVisible(
            @PathVariable Long catalogId,
            @RequestParam boolean visible) {
        return ResponseEntity.ok(equipmentService.setVisible(catalogId, visible));
    }

    @PostMapping("/select")
    @Operation(summary = "Selecionar equipamento em uma linha",
            description = "Grava o valor no registro e, com estoque ligado, reserva/devolve de forma atomica")
    @ApiResponse(responseCode = "200", description = "Selecao aplicada")
    @ApiResponse(responseCode = "400", description = "Equipamento esgotado ou registro inexistente")
    public ResponseEntity<EquipmentSelectionResponse> select(@RequestBody SelectEquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.selectOption(request));
    }

    @DeleteMapping("/catalog/{catalogId}")
    @Operation(summary = "Excluir catalogo")
    @ApiResponse(responseCode = "204", description = "Catalogo excluido")
    public ResponseEntity<Void> deleteCatalog(@PathVariable Long catalogId) {
        equipmentService.deleteCatalog(catalogId);
        return ResponseEntity.noContent().build();
    }
}
