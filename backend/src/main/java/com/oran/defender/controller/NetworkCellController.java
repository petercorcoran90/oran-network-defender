package com.oran.defender.controller;

import com.oran.defender.dto.CellResponse;
import com.oran.defender.service.NetworkCellService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions/{sessionId}/cells")
public class NetworkCellController {

    private final NetworkCellService networkCellService;

    public NetworkCellController(NetworkCellService networkCellService) {
        this.networkCellService = networkCellService;
    }

    // Get cells with their current metrics — pass ?playerId= to get one player's network
    @GetMapping
    public List<CellResponse> getCells(@PathVariable Long sessionId,
                                       @RequestParam(required = false) Long playerId) {
        return networkCellService.getCells(sessionId, playerId).stream().map(CellResponse::from).toList();
    }

    // Get a single cell's details and metrics
    @GetMapping("/{cellId}")
    public CellResponse getCell(@PathVariable Long sessionId,
                                @PathVariable Long cellId) {
        return CellResponse.from(networkCellService.getCell(sessionId, cellId));
    }
}
