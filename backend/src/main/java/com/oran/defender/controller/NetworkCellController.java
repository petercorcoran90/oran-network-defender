package com.oran.defender.controller;

import com.oran.defender.model.NetworkCell;
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

    // Get all cells for a session with their current metrics
    @GetMapping
    public List<NetworkCell> getCells(@PathVariable Long sessionId) {
        return networkCellService.getCells(sessionId);
    }

    // Get a single cell's details and metrics
    @GetMapping("/{cellId}")
    public NetworkCell getCell(@PathVariable Long sessionId,
                               @PathVariable Long cellId) {
        return networkCellService.getCell(sessionId, cellId);
    }
}
