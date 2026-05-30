package com.oran.defender.controller;

import com.oran.defender.dto.HighScoreResponse;
import com.oran.defender.repository.MatchResultRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global high-score table — top finished matches by winner score. */
@RestController
@RequestMapping("/api/highscores")
public class HighScoreController {

    private final MatchResultRepository matchResultRepository;

    public HighScoreController(MatchResultRepository matchResultRepository) {
        this.matchResultRepository = matchResultRepository;
    }

    @GetMapping
    public List<HighScoreResponse> top() {
        return matchResultRepository.findTop20ByOrderByWinnerScoreDesc().stream()
                .map(HighScoreResponse::from).toList();
    }
}
