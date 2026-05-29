package com.oran.defender.service;

import com.oran.defender.engine.RootCause;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.NetworkCell.HealthStatus;
import com.oran.defender.model.Player;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write path for the Python network simulator. The simulator owns metric generation and
 * incident creation; this service is how those land in the game's database. Game clients
 * must never reach these operations (see {@code /api/internal} + the ingest token).
 *
 * <p>Root cause is accepted here (the simulator decides it) but is still hidden from players
 * by {@code IncidentResponse}.
 */
@Service
public class SimulationIngestService {

    public record CellSpec(String cellName, double signalQuality, double userLoad, double latency,
                           double packetLoss, int alarmCount, double energyUsage, String healthStatus) {}

    public record CellsRequest(Long playerId, List<CellSpec> cells) {}

    public record IncidentRequest(Long playerId, Long cellId, String incidentType, String severity,
                                  String rootCause, String description) {}

    public record MetricsRequest(double signalQuality, double userLoad, double latency, double packetLoss,
                                 int alarmCount, double energyUsage, String healthStatus) {}

    private final GameSessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final NetworkCellRepository cellRepository;
    private final IncidentRepository incidentRepository;

    public SimulationIngestService(GameSessionRepository sessionRepository,
                                   PlayerRepository playerRepository,
                                   NetworkCellRepository cellRepository,
                                   IncidentRepository incidentRepository) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.cellRepository = cellRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public List<NetworkCell> createCells(Long sessionId, CellsRequest req) {
        GameSession session = session(sessionId);
        Player player = playerInSession(req.playerId(), sessionId);
        List<NetworkCell> created = new ArrayList<>();
        for (CellSpec spec : req.cells()) {
            NetworkCell cell = new NetworkCell();
            cell.setGameSession(session);
            cell.setPlayer(player);
            cell.setCellName(spec.cellName());
            cell.setSignalQuality(spec.signalQuality());
            cell.setUserLoad(spec.userLoad());
            cell.setLatency(spec.latency());
            cell.setPacketLoss(spec.packetLoss());
            cell.setAlarmCount(spec.alarmCount());
            cell.setEnergyUsage(spec.energyUsage());
            cell.setHealthStatus(healthStatus(spec.healthStatus()));
            created.add(cell);
        }
        return cellRepository.saveAll(created);
    }

    @Transactional
    public Incident createIncident(Long sessionId, IncidentRequest req) {
        GameSession session = session(sessionId);
        Player player = playerInSession(req.playerId(), sessionId);
        NetworkCell cell = cellRepository.findById(req.cellId())
                .orElseThrow(() -> new NotFoundException("Cell not found"));
        if (!cell.getGameSession().getId().equals(sessionId) || !cell.getPlayer().getId().equals(req.playerId())) {
            throw new InvalidActionException("Cell does not belong to that player/session");
        }
        Incident incident = new Incident();
        incident.setGameSession(session);
        incident.setPlayer(player);
        incident.setCell(cell);
        incident.setIncidentType(req.incidentType());
        incident.setSeverity(severity(req.severity()));
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription(req.description());
        incident.setRootCause(rootCause(req.rootCause()));
        incident.setCreatedAt(Instant.now());
        return incidentRepository.save(incident);
    }

    @Transactional
    public NetworkCell updateMetrics(Long cellId, MetricsRequest req) {
        NetworkCell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new NotFoundException("Cell not found"));
        cell.setSignalQuality(req.signalQuality());
        cell.setUserLoad(req.userLoad());
        cell.setLatency(req.latency());
        cell.setPacketLoss(req.packetLoss());
        cell.setAlarmCount(req.alarmCount());
        cell.setEnergyUsage(req.energyUsage());
        cell.setHealthStatus(healthStatus(req.healthStatus()));
        return cellRepository.save(cell);
    }

    private GameSession session(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

    private Player playerInSession(Long playerId, Long sessionId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        if (!player.getGameSession().getId().equals(sessionId)) {
            throw new InvalidActionException("Player is not part of this session");
        }
        return player;
    }

    private HealthStatus healthStatus(String value) {
        try { return HealthStatus.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException e) { throw new InvalidActionException("Unknown health status: " + value); }
    }

    private Severity severity(String value) {
        try { return Severity.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException e) { throw new InvalidActionException("Unknown severity: " + value); }
    }

    private String rootCause(String value) {
        try { return RootCause.valueOf(value).name(); }
        catch (IllegalArgumentException | NullPointerException e) { throw new InvalidActionException("Unknown root cause: " + value); }
    }
}
