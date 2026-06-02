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
@Table(name = "player_actions")
@Getter
@Setter
@NoArgsConstructor
public class PlayerAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActionResult result = ActionResult.PARTIAL;

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded = 0;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    // Not persisted: true when this submission is the first time the player used this action
    // (so the UI can show the one-time "here's the CLI command" teaching modal).
    @jakarta.persistence.Transient
    private boolean newlyLearnedAction;

    public enum ActionResult {
        SUCCESS,
        FAILED,
        PARTIAL
    }
}
