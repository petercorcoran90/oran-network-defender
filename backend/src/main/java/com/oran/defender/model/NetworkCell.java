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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "network_cells")
@Getter
@Setter
@NoArgsConstructor
public class NetworkCell {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    // Head-to-head: each player owns their own mirrored copy of every cell.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "cell_name", nullable = false, length = 80)
    private String cellName;

    @Column(name = "signal_quality", nullable = false)
    private Double signalQuality = 100.0;

    @Column(name = "user_load", nullable = false)
    private Double userLoad = 0.0;

    @Column(nullable = false)
    private Double latency = 0.0;

    @Column(name = "packet_loss", nullable = false)
    private Double packetLoss = 0.0;

    @Column(name = "alarm_count", nullable = false)
    private Integer alarmCount = 0;

    @Column(name = "energy_usage", nullable = false)
    private Double energyUsage = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 16)
    private HealthStatus healthStatus = HealthStatus.GOOD;

    public enum HealthStatus {
        GOOD,
        WARNING,
        CRITICAL
    }
}
