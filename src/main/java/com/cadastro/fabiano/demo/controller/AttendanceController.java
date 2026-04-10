package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ImportAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkAttendanceRequest;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
@Tag(name = "Presença", description = "Importação e controle de presença em listas vinculadas aos formulários")
@SecurityRequirements
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/template/{templateId}/import")
    @Operation(summary = "Importar lista de presença",
            description = "Substitui a lista de presença atual pelo conjunto de linhas enviadas. Cada linha é um Map de colunas")
    @ApiResponse(responseCode = "200", description = "Lista importada com sucesso")
    public ResponseEntity<List<AttendanceRecordResponse>> importAttendance(
            @PathVariable Long templateId,
            @RequestBody ImportAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.importAttendance(templateId, request));
    }

    @GetMapping("/template/{templateId}")
    @Operation(summary = "Listar registros de presença", description = "Retorna os registros paginados, ordenados pela ordem de importação")
    public ResponseEntity<Page<AttendanceRecordResponse>> getByTemplate(
            @PathVariable Long templateId,
            Pageable pageable) {
        return ResponseEntity.ok(attendanceService.getByTemplate(templateId, pageable));
    }

    @GetMapping("/template/existence")
    @Operation(summary = "Verificar existência de presença por templates",
            description = "Retorna um map templateId → boolean indicando se existe lista importada")
    public ResponseEntity<Map<Long, Boolean>> getAttendanceExistence(
            @RequestParam List<Long> templateIds) {
        return ResponseEntity.ok(attendanceService.attendanceExistsForTemplates(templateIds));
    }

    @PatchMapping("/{recordId}/mark")
    @Operation(summary = "Marcar presença", description = "Atualiza o status de presença de um registro individual")
    @ApiResponse(responseCode = "200", description = "Presença atualizada")
    @ApiResponse(responseCode = "400", description = "Registro não encontrado")
    public ResponseEntity<AttendanceRecordResponse> markAttendance(
            @PathVariable Long recordId,
            @RequestBody MarkAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.markAttendance(recordId, request));
    }

    @PatchMapping("/{recordId}/data")
    @Operation(summary = "Atualizar dados da linha", description = "Substitui o Map de colunas de um registro de presença")
    public ResponseEntity<AttendanceRecordResponse> updateRowData(
            @PathVariable Long recordId,
            @RequestBody Map<String, String> rowData) {
        return ResponseEntity.ok(attendanceService.updateRowData(recordId, rowData));
    }

    @DeleteMapping("/{recordId}")
    @Operation(summary = "Excluir registro de presença")
    @ApiResponse(responseCode = "204", description = "Registro excluído")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long recordId) {
        attendanceService.deleteRecord(recordId);
        return ResponseEntity.noContent().build();
    }
}
