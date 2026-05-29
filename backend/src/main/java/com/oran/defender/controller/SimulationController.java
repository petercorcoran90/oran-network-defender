package com.oran.defender.controller;

import com.oran.defender.dto.CellResponse;
import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.service.SimulationIngestService;
import com.oran.defender.service.SimulationIngestService.CellsRequest;
import com.oran.defender.service.SimulationIngestService.IncidentRequest;
import com.oran.defender.service.SimulationIngestService.MetricsRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Internal write endpoints used ONLY by the Python network simulator — never by game clients.
 *
 * <p>Isolation: in Kubernetes this stays a ClusterIP service with no Ingress, so it isn't
 * reachable from outside the cluster. As defence-in-depth, if {@code sim.ingest-token} is set
 * the caller must send a matching {@code X-Internal-Token} header. (No full auth in the
 * project yet — documented in the security notes.)
 */
@RestController
@RequestMapping("/api/internal")
public class SimulationController {

    private final SimulationIngestService ingest;

    @Value("${sim.ingest-token:}")
    private String ingestToken;

    public SimulationController(SimulationIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping("/sessions/{sessionId}/cells")
    public List<CellResponse> createCells(@PathVariable Long sessionId,
                                          @RequestHeader(value = "X-Internal-Token", required = false) String token,
                                          @RequestBody CellsRequest req) {
        authorize(token);
        return ingest.createCells(sessionId, req).stream().map(CellResponse::from).toList();
    }

    @PostMapping("/sessions/{sessionId}/incidents")
    public IncidentResponse createIncident(@PathVariable Long sessionId,
                                           @RequestHeader(value = "X-Internal-Token", required = false) String token,
                                           @RequestBody IncidentRequest req) {
        authorize(token);
        return IncidentResponse.from(ingest.createIncident(sessionId, req));
    }

    @PostMapping("/cells/{cellId}/metrics")
    public CellResponse updateMetrics(@PathVariable Long cellId,
                                      @RequestHeader(value = "X-Internal-Token", required = false) String token,
                                      @RequestBody MetricsRequest req) {
        authorize(token);
        return CellResponse.from(ingest.updateMetrics(cellId, req));
    }

    private void authorize(String token) {
        if (ingestToken != null && !ingestToken.isBlank() && !ingestToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
