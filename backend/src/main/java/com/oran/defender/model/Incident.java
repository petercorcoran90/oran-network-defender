package com.oran.defender.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    // Head-to-head: incidents are mirrored per player. Both players get the same
    // incidentType/severity/rootCause on the same cell, but as separate rows they
    // resolve independently. The owning player is denormalised here so "this player's
    // incidents" is a single indexed lookup (it also matches cell.player).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cell_id", nullable = false)
    private NetworkCell cell;

    @Column(name = "incident_type", nullable = false, length = 80)
    private String incidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity = Severity.LOW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "root_cause", length = 255)
    private String rootCause;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum IncidentStatus {
        OPEN,
        RESOLVED,
        FAILED
    }
}
