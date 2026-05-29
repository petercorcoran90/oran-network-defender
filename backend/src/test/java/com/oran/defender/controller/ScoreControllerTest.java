package com.oran.defender.controller;

import static com.oran.defender.controller.ControllerTestData.player;
import static com.oran.defender.controller.ControllerTestData.scoreEvent;
import static com.oran.defender.controller.ControllerTestData.session;
import static com.oran.defender.controller.ControllerTestData.user;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.GlobalExceptionHandler;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.service.ScoreService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScoreController.class)
@Import(GlobalExceptionHandler.class)
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScoreService scoreService;

    @Test
    void getScoreboardReturnsPlayersOrderedByScore() throws Exception {
        AppUser user = user(3L, "operator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.ACTIVE, user);
        when(scoreService.getScoreboard(11L))
                .thenReturn(List.of(player(21L, user, session, "Blue", 120)));

        mockMvc.perform(get("/api/sessions/{sessionId}/scores", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].username").value("operator"))
                .andExpect(jsonPath("$[0].score").value(120));

        verify(scoreService).getScoreboard(11L);
    }

    @Test
    void getScoreEventsReturnsEventHistory() throws Exception {
        when(scoreService.getScoreEvents(11L))
                .thenReturn(List.of(scoreEvent(71L, "Resolved incident", 50)));

        mockMvc.perform(get("/api/sessions/{sessionId}/scores/events", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(71))
                .andExpect(jsonPath("$[0].reason").value("Resolved incident"))
                .andExpect(jsonPath("$[0].points").value(50));

        verify(scoreService).getScoreEvents(11L);
    }

    @Test
    void getScoreEventsMapsMissingSessionToNotFound() throws Exception {
        when(scoreService.getScoreEvents(99L))
                .thenThrow(new NotFoundException("Session not found"));

        mockMvc.perform(get("/api/sessions/{sessionId}/scores/events", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found"));
    }

    @Test
    void getScoreboardMapsMissingSessionToNotFound() throws Exception {
        when(scoreService.getScoreboard(99L))
                .thenThrow(new NotFoundException("Session not found"));

        mockMvc.perform(get("/api/sessions/{sessionId}/scores", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found"));
    }
}
