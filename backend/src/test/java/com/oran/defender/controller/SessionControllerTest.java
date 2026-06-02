package com.oran.defender.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link SessionController}: routing + JSON shape on the happy path, and the
 * negative cases (Bean Validation rejections, error-to-status mapping via GlobalExceptionHandler).
 * The service is mocked, so this exercises only the controller + validation + advice slice.
 */
@WebMvcTest(SessionController.class)
@DisplayName("SessionController web layer")
class SessionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SessionService sessionService;

    private GameSession sampleSession() {
        GameSession s = new GameSession();
        s.setId(1L);
        s.setSessionCode("ABC123");
        s.setName("Friday match");
        s.setStatus(SessionStatus.WAITING);
        s.setDifficulty(Difficulty.MEDIUM);
        s.setDurationSeconds(300);
        AppUser creator = new AppUser();
        creator.setId(7L);
        s.setCreatedByUser(creator);
        return s;
    }

    @Test
    @DisplayName("POST /api/sessions -> 201 with the created session")
    void createValid() throws Exception {
        given(sessionService.createSession(eq("Friday match"), eq(7L), eq(300), any()))
                .willReturn(sampleSession());

        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Friday match\",\"createdByUserId\":7,\"durationSeconds\":300,\"difficulty\":\"MEDIUM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sessionCode").value("ABC123"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.createdByUserId").value(7));
    }

    @Test
    @DisplayName("POST /api/sessions with a blank name -> 400, service untouched")
    void createBlankName() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"createdByUserId\":7}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("POST /api/sessions without createdByUserId -> 400")
    void createMissingUser() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Match\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("POST /api/sessions with a non-positive duration -> 400")
    void createBadDuration() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Match\",\"createdByUserId\":7,\"durationSeconds\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("GET /api/sessions/{id} unknown -> 404 with a clean error body")
    void getMissing() throws Exception {
        given(sessionService.getSession(99L)).willThrow(new NotFoundException("Session not found"));

        mvc.perform(get("/api/sessions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Session not found"));
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/join -> 201 with the player")
    void joinValid() throws Exception {
        Player p = new Player();
        p.setId(2L);
        p.setTeamName("Blue");
        p.setScore(0);
        given(sessionService.joinSession(eq(1L), eq(7L), any())).willReturn(p);

        mvc.perform(post("/api/sessions/1/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"teamName\":\"Blue\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.teamName").value("Blue"));
    }

    @Test
    @DisplayName("POST join without userId -> 400")
    void joinMissingUser() throws Exception {
        mvc.perform(post("/api/sessions/1/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Blue\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("POST join to a full session -> 409")
    void joinFull() throws Exception {
        given(sessionService.joinSession(eq(1L), eq(7L), any()))
                .willThrow(new ConflictException("Session is full"));

        mvc.perform(post("/api/sessions/1/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Session is full"));
    }

    @Test
    @DisplayName("POST /api/sessions/training -> 201 with the active solo session + playerId")
    void startTraining() throws Exception {
        GameSession s = new GameSession();
        s.setId(9L);
        s.setSessionCode("TRN001");
        s.setName("ava — training");
        s.setStatus(SessionStatus.ACTIVE);
        s.setDifficulty(Difficulty.EASY);
        s.setMode(GameSession.Mode.TRAINING);
        s.setDurationSeconds(300);
        Player p = new Player();
        p.setId(2L);
        p.setGameSession(s);
        given(sessionService.createTrainingSession(7L, 300)).willReturn(p);

        mvc.perform(post("/api/sessions/training").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"durationSeconds\":300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(2))
                .andExpect(jsonPath("$.session.status").value("ACTIVE"))
                .andExpect(jsonPath("$.session.mode").value("TRAINING"))
                .andExpect(jsonPath("$.session.difficulty").value("EASY"));
    }

    @Test
    @DisplayName("POST training without userId -> 400")
    void startTrainingMissingUser() throws Exception {
        mvc.perform(post("/api/sessions/training").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\":300}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sessionService);
    }
}
