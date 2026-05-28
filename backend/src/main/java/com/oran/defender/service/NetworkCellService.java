package com.oran.defender.service;

import com.oran.defender.model.NetworkCell;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkCellService {

    // TODO: inject NetworkCellRepository

    public List<NetworkCell> getCells(Long sessionId) {
        // TODO: return all cells for the session
        throw new UnsupportedOperationException("Not implemented");
    }

    public NetworkCell getCell(Long sessionId, Long cellId) {
        // TODO: find cell by ID, verify it belongs to sessionId, or throw 404
        throw new UnsupportedOperationException("Not implemented");
    }
}
