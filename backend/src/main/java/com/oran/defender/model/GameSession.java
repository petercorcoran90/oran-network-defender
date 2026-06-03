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
@Table(name = "game_sessions")
@Getter
@Setter
@NoArgsConstructor
public class GameSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 32)
    private String sessionCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.WAITING;

    // Match length in seconds, chosen by the creator. The session ends when
    // started_at + duration_seconds has elapsed; highest score at that point wins.
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 300;

    // Chosen by the creator; the simulator maps this to a tower count (EASY 3 / MEDIUM 6 / HARD 9).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @org.hibernate.annotations.ColumnDefault("'MEDIUM'")
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    // Set when a player ragequits — that player forfeits, the other wins regardless of score.
    @Column(name = "forfeited_by_player_id")
    private Long forfeitedByPlayerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdByUser;

    // HEAD_TO_HEAD = the competitive 2-player match; TRAINING = the solo curriculum mode.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @org.hibernate.annotations.ColumnDefault("'HEAD_TO_HEAD'")
    private Mode mode = Mode.HEAD_TO_HEAD;

    public enum SessionStatus {
        WAITING,
        ACTIVE,
        ENDED
    }

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum Mode {
        HEAD_TO_HEAD,
        TRAINING
    }
}
