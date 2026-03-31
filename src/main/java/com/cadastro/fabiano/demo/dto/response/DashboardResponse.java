package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record DashboardResponse(
        long totalTemplates,
        long totalClients,
        long totalSubmissions,
        long totalAppointments,
        long confirmedAppointments,
        long cancelledAppointments,
        long totalAttendanceRecords,
        long presentAttendanceRecords,
        List<TemplateStatResponse> templates
) {}
