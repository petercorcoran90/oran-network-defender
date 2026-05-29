package com.oran.defender.controller;

import com.oran.defender.dto.PlayerResponse;
import com.oran.defender.dto.SessionResponse;
import com.oran.defender.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    // durationSeconds is optional; when omitted the service applies a default match length.
    record CreateSessionRequest(@NotBlank String name,
                                @NotNull Long createdByUserId,
                                @Positive Integer durationSeconds) {}
    record JoinSessionRequest(@NotNull Long userId, String teamName) {}

    // Create a new game session (status = WAITING)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@Valid @RequestBody CreateSessionRequest req) {
        return SessionResponse.from(sessionService.createSession(req.name(), req.createdByUserId(), req.durationSeconds()));
    }

    // List all sessions with status WAITING or ACTIVE
    @GetMapping
    public List<SessionResponse> listSessions() {
        return sessionService.listActiveSessions().stream()
                .map(SessionResponse::from)
                .toList();
    }

    // Get a single session by ID
    @GetMapping("/{id}")
    public SessionResponse getSession(@PathVariable Long id) {
        return SessionResponse.from(sessionService.getSession(id));
    }

    // Join an existing session (creates a Player record)
    @PostMapping("/{id}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerResponse joinSession(@PathVariable Long id,
                                      @Valid @RequestBody JoinSessionRequest req) {
        return PlayerResponse.from(sessionService.joinSession(id, req.userId(), req.teamName()));
    }

    // Start the session - transitions status from WAITING to ACTIVE
    @PostMapping("/{id}/start")
    public SessionResponse startSession(@PathVariable Long id) {
        return SessionResponse.from(sessionService.startSession(id));
    }

    // Get all players in a session (ordered by score desc)
    @GetMapping("/{id}/players")
    public List<PlayerResponse> getPlayers(@PathVariable Long id) {
        return sessionService.getPlayers(id).stream()
                .map(PlayerResponse::from)
                .toList();
    }
}
