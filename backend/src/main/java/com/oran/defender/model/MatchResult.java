package com.oran.defender.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The final result of a finished match — one row per ended session. Powers the high-score
 * table. Stored once when a session reaches ENDED (timer expiry or a forfeit).
 */
@Entity
@Table(name = "match_results")
@Getter
@Setter
@NoArgsConstructor
public class MatchResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_session_id", nullable = false, unique = true)
    private Long gameSessionId;

    @Column(name = "winner_name", nullable = false, length = 80)
    private String winnerName;

    @Column(name = "winner_score", nullable = false)
    private Integer winnerScore;

    @Column(name = "loser_name", nullable = false, length = 80)
    private String loserName;

    @Column(nullable = false, length = 16)
    private String difficulty;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(nullable = false)
    private boolean forfeit = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
