package com.cadastro.fabiano.demo.dto.request;

public record ViewConfigRequest(
        Boolean viewAllowExport,
        Boolean viewShowSubmissions,
        Boolean viewShowAttendance,
        Boolean viewShowAppointments,
        Boolean viewAllowAttendanceCheck,
        // Visibilidade das colunas internas da lista de presenca
        Boolean attendanceShowCompanions,
        Boolean attendanceShowPresence,
        Boolean attendanceShowNotes,
        Boolean attendanceShowMarkedAt
) {}
