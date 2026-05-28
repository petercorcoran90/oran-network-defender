package com.oran.defender.controller;

import com.oran.defender.model.GameSession;
import com.oran.defender.model.Player;
import com.oran.defender.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    record CreateSessionRequest(@NotBlank String name, @NotNull Long createdByUserId) {}
    record JoinSessionRequest(@NotNull Long userId, String teamName) {}

    // Create a new game session (status = WAITING)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameSession createSession(@Valid @RequestBody CreateSessionRequest req) {
        return sessionService.createSession(req.name(), req.createdByUserId());
    }

    // List all sessions with status WAITING or ACTIVE
    @GetMapping
    public List<GameSession> listSessions() {
        return sessionService.listActiveSessions();
    }

    // Get a single session by ID
    @GetMapping("/{id}")
    public GameSession getSession(@PathVariable Long id) {
        return sessionService.getSession(id);
    }

    // Join an existing session (creates a Player record)
    @PostMapping("/{id}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public Player joinSession(@PathVariable Long id,
                              @Valid @RequestBody JoinSessionRequest req) {
        return sessionService.joinSession(id, req.userId(), req.teamName());
    }

    // Start the session — transitions status from WAITING to ACTIVE
    @PostMapping("/{id}/start")
    public GameSession startSession(@PathVariable Long id) {
        return sessionService.startSession(id);
    }

    // Get all players in a session (ordered by score desc)
    @GetMapping("/{id}/players")
    public List<Player> getPlayers(@PathVariable Long id) {
        return sessionService.getPlayers(id);
    }
}
