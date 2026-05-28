package com.oran.defender.controller;

import com.oran.defender.model.Player;
import com.oran.defender.model.ScoreEvent;
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
    public List<Player> getScoreboard(@PathVariable Long sessionId) {
        return scoreService.getScoreboard(sessionId);
    }

    // Get the full score event history for a session (why points were awarded/deducted)
    @GetMapping("/events")
    public List<ScoreEvent> getScoreEvents(@PathVariable Long sessionId) {
        return scoreService.getScoreEvents(sessionId);
    }
}
