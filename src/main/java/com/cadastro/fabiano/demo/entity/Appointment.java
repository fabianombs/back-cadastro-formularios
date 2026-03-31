package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_template_id", nullable = false)
    private FormTemplate formTemplate;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "booked_by_name")
    private String bookedByName;

    @Column(name = "booked_by_contact")
    private String bookedByContact;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ElementCollection
    @CollectionTable(
        name = "appointment_extra_values",
        joinColumns = @JoinColumn(name = "appointment_id")
    )
    @MapKeyColumn(name = "field_label")
    @Column(name = "field_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> extraValues = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AppointmentStatus.AGENDADO;
        }
    }
}
