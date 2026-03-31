package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ImportAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkAttendanceRequest;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.service.AttendanceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/template/{templateId}/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<List<AttendanceRecordResponse>> importAttendance(
            @PathVariable Long templateId,
            @RequestBody ImportAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.importAttendance(templateId, request));
    }

    @GetMapping("/template/{templateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<Page<AttendanceRecordResponse>> getByTemplate(
            @PathVariable Long templateId,
            Pageable pageable) {
        return ResponseEntity.ok(attendanceService.getByTemplate(templateId, pageable));
    }

    @PatchMapping("/{recordId}/mark")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<AttendanceRecordResponse> markAttendance(
            @PathVariable Long recordId,
            @RequestBody MarkAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.markAttendance(recordId, request));
    }

    @PatchMapping("/{recordId}/data")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<AttendanceRecordResponse> updateRowData(
            @PathVariable Long recordId,
            @RequestBody Map<String, String> rowData) {
        return ResponseEntity.ok(attendanceService.updateRowData(recordId, rowData));
    }

    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long recordId) {
        attendanceService.deleteRecord(recordId);
        return ResponseEntity.noContent().build();
    }
}
