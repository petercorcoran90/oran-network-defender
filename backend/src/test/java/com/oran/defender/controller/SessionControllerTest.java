package com.oran.defender.controller;

import static com.oran.defender.controller.ControllerTestData.player;
import static com.oran.defender.controller.ControllerTestData.session;
import static com.oran.defender.controller.ControllerTestData.user;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.GlobalExceptionHandler;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.service.SessionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@Import(GlobalExceptionHandler.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @Test
    void createSessionReturnsCreatedSession() throws Exception {
        AppUser creator = user(7L, "creator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.WAITING, creator);
        when(sessionService.createSession("Training", 7L, 120)).thenReturn(session);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Training","createdByUserId":7,"durationSeconds":120}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.sessionCode").value("ABC123"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.createdByUserId").value(7))
                .andExpect(jsonPath("$.createdByUsername").value("creator"));

        verify(sessionService).createSession("Training", 7L, 120);
    }

    @Test
    void createSessionAllowsOmittedDuration() throws Exception {
        AppUser creator = user(7L, "creator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.WAITING, creator);
        when(sessionService.createSession("Training", 7L, null)).thenReturn(session);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Training","createdByUserId":7}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.durationSeconds").value(300));

        verify(sessionService).createSession("Training", 7L, null);
    }

    @Test
    void createSessionRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","createdByUserId":7,"durationSeconds":120}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("name must not be blank"));

        verifyNoInteractions(sessionService);
    }

    @Test
    void createSessionRejectsMissingCreator() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Training","durationSeconds":120}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("createdByUserId must not be null"));

        verifyNoInteractions(sessionService);
    }

    @Test
    void createSessionRejectsNonPositiveDuration() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Training","createdByUserId":7,"durationSeconds":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(sessionService);
    }

    @Test
    void listSessionsReturnsSessionResponses() throws Exception {
        AppUser creator = user(7L, "creator", "PLAYER");
        when(sessionService.listActiveSessions()).thenReturn(List.of(
                session(11L, "ABC123", "Training", SessionStatus.WAITING, creator),
                session(12L, "DEF456", "Match", SessionStatus.ACTIVE, creator)));

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    void getSessionReturnsSession() throws Exception {
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.WAITING, null);
        when(sessionService.getSession(11L)).thenReturn(session);

        mockMvc.perform(get("/api/sessions/{id}", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.createdByUserId").doesNotExist())
                .andExpect(jsonPath("$.createdByUsername").doesNotExist());

        verify(sessionService).getSession(11L);
    }

    @Test
    void getSessionMapsMissingSessionToNotFound() throws Exception {
        when(sessionService.getSession(99L)).thenThrow(new NotFoundException("Session not found"));

        mockMvc.perform(get("/api/sessions/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found"));
    }

    @Test
    void joinSessionReturnsCreatedPlayer() throws Exception {
        AppUser user = user(3L, "operator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.WAITING, user);
        Player player = player(21L, user, session, "Blue", 0);
        when(sessionService.joinSession(11L, 3L, "Blue")).thenReturn(player);

        mockMvc.perform(post("/api/sessions/{id}/join", 11L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":3,"teamName":"Blue"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.sessionId").value(11))
                .andExpect(jsonPath("$.teamName").value("Blue"));

        verify(sessionService).joinSession(11L, 3L, "Blue");
    }

    @Test
    void joinSessionRejectsMissingUserId() throws Exception {
        mockMvc.perform(post("/api/sessions/{id}/join", 11L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamName":"Blue"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(sessionService);
    }

    @Test
    void startSessionMapsConflict() throws Exception {
        when(sessionService.startSession(11L))
                .thenThrow(new ConflictException("Session needs 2 players to start"));

        mockMvc.perform(post("/api/sessions/{id}/start", 11L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Session needs 2 players to start"));
    }

    @Test
    void startSessionReturnsActiveSession() throws Exception {
        AppUser creator = user(7L, "creator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.ACTIVE, creator);
        when(sessionService.startSession(11L)).thenReturn(session);

        mockMvc.perform(post("/api/sessions/{id}/start", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(sessionService).startSession(11L);
    }

    @Test
    void getPlayersReturnsPlayerResponses() throws Exception {
        AppUser user = user(3L, "operator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.ACTIVE, user);
        when(sessionService.getPlayers(11L)).thenReturn(List.of(player(21L, user, session, "Blue", 25)));

        mockMvc.perform(get("/api/sessions/{id}/players", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].score").value(25));
    }
}
