package com.cadastro.fabiano.demo.dto.request;

public record ViewConfigRequest(
        Boolean viewAllowExport,
        Boolean viewShowSubmissions,
        Boolean viewShowAttendance,
        Boolean viewShowAppointments
) {}
