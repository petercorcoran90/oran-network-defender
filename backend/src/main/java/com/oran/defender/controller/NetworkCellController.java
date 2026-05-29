package com.oran.defender.controller;

import com.oran.defender.dto.NetworkCellResponse;
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
    public List<NetworkCellResponse> getCells(@PathVariable Long sessionId) {
        return networkCellService.getCells(sessionId).stream()
                .map(NetworkCellResponse::from)
                .toList();
    }

    // Get a single cell's details and metrics
    @GetMapping("/{cellId}")
    public NetworkCellResponse getCell(@PathVariable Long sessionId,
                                       @PathVariable Long cellId) {
        return NetworkCellResponse.from(networkCellService.getCell(sessionId, cellId));
    }
}
