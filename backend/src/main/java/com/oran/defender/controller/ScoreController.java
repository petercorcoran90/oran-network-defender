package com.oran.defender.controller;

import com.oran.defender.dto.PlayerResponse;
import com.oran.defender.dto.ScoreEventResponse;
import com.oran.defender.service.ScoreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions/{sessionId}/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    // Get the scoreboard — all players ordered by score descending
    @GetMapping
    public List<PlayerResponse> getScoreboard(@PathVariable Long sessionId) {
        return scoreService.getScoreboard(sessionId).stream().map(PlayerResponse::from).toList();
    }

    // Get the full score event history for a session (why points were awarded/deducted)
    @GetMapping("/events")
    public List<ScoreEventResponse> getScoreEvents(@PathVariable Long sessionId) {
        return scoreService.getScoreEvents(sessionId).stream().map(ScoreEventResponse::from).toList();
    }
}
