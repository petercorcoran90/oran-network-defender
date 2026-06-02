package com.oran.defender.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.dto.PlayerResponse;
import com.oran.defender.dto.SessionResponse;
import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.service.SessionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SessionController unit tests")
class SessionControllerTest {

    private SessionController sessionController;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        sessionController = new SessionController(sessionService);
    }

    @Test
    @DisplayName("createSession returns session response")
    void createSession_success() {
        SessionController.CreateSessionRequest req =
                new SessionController.CreateSessionRequest("Friday match", 7L, 300, "MEDIUM");
        GameSession session = buildSession();

        when(sessionService.createSession("Friday match", 7L, 300, "MEDIUM")).thenReturn(session);

        SessionResponse result = sessionController.createSession(req);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("ABC123", result.sessionCode());
        assertEquals("WAITING", result.status());
        verify(sessionService, times(1)).createSession("Friday match", 7L, 300, "MEDIUM");
    }

    @Test
    @DisplayName("listSessions returns all active sessions")
    void listSessions_success() {
        when(sessionService.listActiveSessions()).thenReturn(List.of(buildSession()));

        List<SessionResponse> result = sessionController.listSessions();

        assertEquals(1, result.size());
        assertEquals("ABC123", result.get(0).sessionCode());
    }

    @Test
    @DisplayName("getSession returns session by id")
    void getSession_success() {
        when(sessionService.getSession(1L)).thenReturn(buildSession());

        SessionResponse result = sessionController.getSession(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(sessionService, times(1)).getSession(1L);
    }

    @Test
    @DisplayName("getSession throws NotFoundException when session does not exist")
    void getSession_notFound() {
        when(sessionService.getSession(99L)).thenThrow(new NotFoundException("Session not found"));

        assertThrows(NotFoundException.class, () -> sessionController.getSession(99L));
    }

    @Test
    @DisplayName("getByCode returns session for matching code")
    void getByCode_success() {
        when(sessionService.getByCode("ABC123")).thenReturn(buildSession());

        SessionResponse result = sessionController.getByCode("ABC123");

        assertNotNull(result);
        assertEquals("ABC123", result.sessionCode());
        verify(sessionService, times(1)).getByCode("ABC123");
    }

    @Test
    @DisplayName("getByCode throws NotFoundException when code does not match")
    void getByCode_notFound() {
        when(sessionService.getByCode("XXXXXX"))
                .thenThrow(new NotFoundException("No match with that code"));

        assertThrows(NotFoundException.class, () -> sessionController.getByCode("XXXXXX"));
    }

    @Test
    @DisplayName("joinSession returns player response")
    void joinSession_success() {
        SessionController.JoinSessionRequest req =
                new SessionController.JoinSessionRequest(7L, "Blue");
        Player player = buildPlayer();

        when(sessionService.joinSession(1L, 7L, "Blue")).thenReturn(player);

        PlayerResponse result = sessionController.joinSession(1L, req);

        assertNotNull(result);
        assertEquals(2L, result.id());
        assertEquals("Blue", result.teamName());
        verify(sessionService, times(1)).joinSession(1L, 7L, "Blue");
    }

    @Test
    @DisplayName("joinSession throws ConflictException when session is full")
    void joinSession_full() {
        SessionController.JoinSessionRequest req =
                new SessionController.JoinSessionRequest(7L, "Blue");

        when(sessionService.joinSession(1L, 7L, "Blue"))
                .thenThrow(new ConflictException("Session is full"));

        assertThrows(ConflictException.class, () -> sessionController.joinSession(1L, req));
    }

    @Test
    @DisplayName("ready marks player ready and returns session")
    void ready_success() {
        SessionController.ReadyRequest req = new SessionController.ReadyRequest(2L);

        when(sessionService.markReady(1L, 2L)).thenReturn(buildSession());

        SessionResponse result = sessionController.ready(1L, req);

        assertNotNull(result);
        verify(sessionService, times(1)).markReady(1L, 2L);
    }

    @Test
    @DisplayName("leave ends session and returns final state")
    void leave_success() {
        SessionController.LeaveRequest req = new SessionController.LeaveRequest(2L);
        GameSession ended = buildSession();
        ended.setStatus(SessionStatus.ENDED);

        when(sessionService.leaveSession(1L, 2L)).thenReturn(ended);

        SessionResponse result = sessionController.leave(1L, req);

        assertEquals("ENDED", result.status());
        verify(sessionService, times(1)).leaveSession(1L, 2L);
    }

    @Test
    @DisplayName("startSession transitions session to ACTIVE")
    void startSession_success() {
        GameSession active = buildSession();
        active.setStatus(SessionStatus.ACTIVE);

        when(sessionService.startSession(1L)).thenReturn(active);

        SessionResponse result = sessionController.startSession(1L);

        assertEquals("ACTIVE", result.status());
        verify(sessionService, times(1)).startSession(1L);
    }

    @Test
    @DisplayName("getPlayers returns list of players ordered by score")
    void getPlayers_success() {
        when(sessionService.getPlayers(1L)).thenReturn(List.of(buildPlayer()));

        List<PlayerResponse> result = sessionController.getPlayers(1L);

        assertEquals(1, result.size());
        assertEquals("Blue", result.get(0).teamName());
        verify(sessionService, times(1)).getPlayers(1L);
    }

    private GameSession buildSession() {
        AppUser creator = new AppUser();
        creator.setId(7L);

        GameSession s = new GameSession();
        s.setId(1L);
        s.setSessionCode("ABC123");
        s.setName("Friday match");
        s.setStatus(SessionStatus.WAITING);
        s.setDifficulty(Difficulty.MEDIUM);
        s.setDurationSeconds(300);
        s.setCreatedByUser(creator);
        return s;
    }

    private Player buildPlayer() {
        Player p = new Player();
        p.setId(2L);
        p.setTeamName("Blue");
        p.setScore(0);
        p.setReady(true);
        return p;
    }
}
