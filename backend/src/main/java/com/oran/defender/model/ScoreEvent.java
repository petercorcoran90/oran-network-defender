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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable, append-only record of every score change. The player's running
 * {@code score} is the sum of these; storing each delta gives us the end-of-game
 * breakdown ("best/worst decision") and an audit trail so scores can't be silently edited.
 */
@Entity
@Table(name = "score_events")
@Getter
@Setter
@NoArgsConstructor
public class ScoreEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
