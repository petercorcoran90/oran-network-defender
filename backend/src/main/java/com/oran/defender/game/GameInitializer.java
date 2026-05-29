package com.oran.defender.game;

import com.oran.defender.engine.RootCause;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.NetworkCell.HealthStatus;
import com.oran.defender.model.Player;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Seeds the game world when a match starts. Each player gets their own private copy of an
 * identical network, and the same incidents are placed on both copies — so the only thing
 * that differs between the two players is how they respond. The hidden root cause on each
 * incident is a {@link RootCause} the engine already knows how to evaluate.
 *
 * <p>This is the in-Java stand-in for the Python simulator: it produces the initial cells +
 * incidents. Ongoing metric drift and new incidents over time are a later increment.
 */
@Component
public class
GameInitializer {

    private static final int CELL_COUNT = 6;

    /** Which cell (by index) carries which incident. Applied identically to both players. */
    private record IncidentPlan(int cellIndex, RootCause rootCause, Severity severity,
                                String type, String description) {}

    private static final List<IncidentPlan> PLAN = List.of(
            new IncidentPlan(2, RootCause.CELL_OVERLOAD, Severity.HIGH, "Cell Overload",
                    "User load and latency are climbing past safe thresholds on this cell."),
            new IncidentPlan(4, RootCause.NEIGHBOUR_CONFIG_CHANGE, Severity.MEDIUM, "Config Drift",
                    "Packet loss is rising after a neighbouring cell's configuration changed."),
            new IncidentPlan(0, RootCause.ALARM_STORM, Severity.HIGH, "Alarm Storm",
                    "A burst of alarms is masking the underlying fault on this cell."),
            new IncidentPlan(5, RootCause.FALSE_ALARM, Severity.LOW, "Suspected False Alarm",
                    "An alert fired but this cell's metrics look healthy."));

    private final NetworkCellRepository cellRepository;
    private final IncidentRepository incidentRepository;

    public GameInitializer(NetworkCellRepository cellRepository, IncidentRepository incidentRepository) {
        this.cellRepository = cellRepository;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Builds each player's network and seeds the shared incident set. Idempotent: if the
     * session already has cells, it does nothing (so a re-start can't double-seed).
     */
    public void setUpNetwork(GameSession session, List<Player> players) {
        if (!cellRepository.findByGameSessionId(session.getId()).isEmpty()) {
            return;
        }
        for (Player player : players) {
            List<NetworkCell> cells = new ArrayList<>(CELL_COUNT);
            for (int i = 0; i < CELL_COUNT; i++) {
                cells.add(healthyCell(session, player, i));
            }
            // Make the affected cells' metrics tell the same story as their hidden root cause.
            for (IncidentPlan plan : PLAN) {
                applySymptoms(cells.get(plan.cellIndex()), plan.rootCause());
            }
            cellRepository.saveAll(cells); // persist cells first so incidents can reference them
            for (IncidentPlan plan : PLAN) {
                createIncident(session, player, cells.get(plan.cellIndex()), plan);
            }
        }
    }

    private NetworkCell healthyCell(GameSession session, Player player, int index) {
        NetworkCell cell = new NetworkCell();
        cell.setGameSession(session);
        cell.setPlayer(player);
        cell.setCellName(String.format("Cell-%02d", index + 1));
        cell.setSignalQuality(95.0);
        cell.setUserLoad(30.0);
        cell.setLatency(25.0);
        cell.setPacketLoss(1.0);
        cell.setAlarmCount(0);
        cell.setEnergyUsage(45.0);
        cell.setHealthStatus(HealthStatus.GOOD);
        return cell;
    }

    private void applySymptoms(NetworkCell cell, RootCause rootCause) {
        switch (rootCause) {
            case CELL_OVERLOAD -> { cell.setUserLoad(95.0); cell.setLatency(180.0); cell.setHealthStatus(HealthStatus.CRITICAL); }
            case NEIGHBOUR_CONFIG_CHANGE -> { cell.setPacketLoss(14.0); cell.setLatency(70.0); cell.setHealthStatus(HealthStatus.WARNING); }
            case ALARM_STORM -> { cell.setAlarmCount(12); cell.setHealthStatus(HealthStatus.WARNING); }
            case TRANSPORT_LINK_FAULT -> { cell.setPacketLoss(22.0); cell.setLatency(140.0); cell.setHealthStatus(HealthStatus.CRITICAL); }
            case NEIGHBOUR_INTERFERENCE -> { cell.setSignalQuality(55.0); cell.setHealthStatus(HealthStatus.WARNING); }
            case SOFTWARE_UPGRADE_FAULT -> { cell.setPacketLoss(9.0); cell.setHealthStatus(HealthStatus.WARNING); }
            case ROGUE_AUTOMATION -> { cell.setUserLoad(80.0); cell.setLatency(120.0); cell.setHealthStatus(HealthStatus.WARNING); }
            case FALSE_ALARM -> cell.setAlarmCount(1); // otherwise healthy — the trap is acting on it
        }
    }

    private void createIncident(GameSession session, Player player, NetworkCell cell, IncidentPlan plan) {
        Incident incident = new Incident();
        incident.setGameSession(session);
        incident.setPlayer(player);
        incident.setCell(cell);
        incident.setIncidentType(plan.type());
        incident.setSeverity(plan.severity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription(plan.description());
        incident.setRootCause(plan.rootCause().name());
        incident.setCreatedAt(Instant.now());
        incidentRepository.save(incident);
    }
}
