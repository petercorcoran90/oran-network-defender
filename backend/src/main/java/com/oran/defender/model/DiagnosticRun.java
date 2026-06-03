package com.oran.defender.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A diagnostic a player ran while investigating an incident, and the evidence it produced. One row
 * per (incident, player, diagnostic) — re-running the same diagnostic is idempotent (no extra cost).
 * Enums are stored as their names, matching how {@code Incident.rootCause} is persisted.
 */
@Entity
@Table(name = "diagnostic_runs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"incident_id", "player_id", "diagnostic_type"}))
@Getter
@Setter
@NoArgsConstructor
public class DiagnosticRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "diagnostic_type", nullable = false, length = 40)
    private String diagnosticType;   // DiagnosticType name

    @Column(nullable = false, length = 16)
    private String result;           // EvidenceResult name (CONFIRMS / RULES_OUT)

    @Column(nullable = false, length = 40)
    private String implicated;       // RootCause name the diagnostic tested for

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
