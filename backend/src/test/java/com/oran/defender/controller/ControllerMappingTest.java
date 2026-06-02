package com.oran.defender.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oran.defender.dto.ActionResponse;
import com.oran.defender.dto.CellResponse;
import com.oran.defender.dto.HighScoreResponse;
import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.dto.PlayerActionResponse;
import com.oran.defender.dto.PlayerResponse;
import com.oran.defender.dto.ScoreEventResponse;
import com.oran.defender.dto.SessionResponse;
import com.oran.defender.model.Action;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.MatchResult;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.NetworkCell.ConfigStatus;
import com.oran.defender.model.NetworkCell.HealthStatus;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.model.ScoreEvent;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.MatchResultRepository;
import com.oran.defender.service.IncidentService;
import com.oran.defender.service.NetworkCellService;
import com.oran.defender.service.ScoreService;
import com.oran.defender.service.SessionService;
import com.oran.defender.service.UserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

@DisplayName("Controller direct mapping")
class ControllerMappingTest {

    private ActionRepository actionRepository;
    private MatchResultRepository matchResultRepository;
    private ScoreService scoreService;
    private NetworkCellService cellService;
    private SessionService sessionService;
    private IncidentService incidentService;
    private UserService userService;

    private ActionController actionController;
    private HighScoreController highScoreController;
    private ScoreController scoreController;
    private NetworkCellController cellController;
    private SessionController sessionController;
    private IncidentController incidentController;
    private UserController userController;

    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);

        actionRepository = mock(ActionRepository.class);
        matchResultRepository = mock(MatchResultRepository.class);
        scoreService = mock(ScoreService.class);
        cellService = mock(NetworkCellService.class);
        sessionService = mock(SessionService.class);
        incidentService = mock(IncidentService.class);
        userService = mock(UserService.class);

        actionController = new ActionController(actionRepository);
        highScoreController = new HighScoreController(matchResultRepository);
        scoreController = new ScoreController(scoreService);
        cellController = new NetworkCellController(cellService);
        sessionController = new SessionController(sessionService);
        incidentController = new IncidentController(incidentService);
        userController = new UserController(userService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    @DisplayName("ActionController maps actions to DTOs")
    void actionControllerMapsResponses() {
        Action action = action(4L, "REBALANCE_TRAFFIC");
        when(actionRepository.findAll()).thenReturn(List.of(action));

        List<ActionResponse> actionResponses = actionController.getActions();

        assertNotNull(actionResponses);
        assertEquals(1, actionResponses.size());
        ActionResponse response = actionResponses.get(0);
        assertEquals(4L, response.id());
        assertEquals("REBALANCE_TRAFFIC", response.actionName());
        assertEquals("Action REBALANCE_TRAFFIC", response.description());
        verify(actionRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("HighScoreController maps match results to DTOs")
    void highScoreControllerMapsResponses() {
        MatchResult result = new MatchResult();
        result.setWinnerName("Blue");
        result.setWinnerScore(150);
        result.setLoserName("Red");
        result.setDifficulty("HARD");
        result.setDurationSeconds(600);
        result.setForfeit(true);
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        result.setCreatedAt(createdAt);
        when(matchResultRepository.findTop20ByOrderByWinnerScoreDesc()).thenReturn(List.of(result));

        List<HighScoreResponse> highscores = highScoreController.top();

        assertNotNull(highscores);
        assertEquals(1, highscores.size());
        HighScoreResponse response = highscores.get(0);
        assertEquals("Blue", response.winnerName());
        assertEquals(150, response.winnerScore());
        assertEquals("Red", response.loserName());
        assertEquals("HARD", response.difficulty());
        assertEquals(600, response.durationSeconds());
        assertTrue(response.forfeit());
        assertEquals(createdAt, response.createdAt());
        verify(matchResultRepository, times(1)).findTop20ByOrderByWinnerScoreDesc();
    }

    @Test
    @DisplayName("ScoreController returns scoreboard ordered by score")
    void scoreControllerGetScoreboard() {
        GameSession session = session(1L, user(7L, "creator"));
        Player blue = player(2L, user(8L, "blue"), session, "Blue", 120, true);
        when(scoreService.getScoreboard(1L)).thenReturn(List.of(blue));

        List<PlayerResponse> scores = scoreController.getScoreboard(1L);

        assertNotNull(scores);
        assertEquals(1, scores.size());
        PlayerResponse response = scores.get(0);
        assertEquals(2L, response.id());
        assertEquals("Blue", response.teamName());
        assertEquals(120, response.score());
        assertTrue(response.ready());
        verify(scoreService, times(1)).getScoreboard(1L);
    }

    @Test
    @DisplayName("ScoreController returns score events history")
    void scoreControllerGetScoreEvents() {
        Player blue = player(2L, user(8L, "blue"), session(1L, user(7L, "creator")), "Blue", 120, true);
        ScoreEvent event = new ScoreEvent();
        event.setId(9L);
        event.setPlayer(blue);
        event.setGameSession(session(1L, user(7L, "creator")));
        event.setReason("Cell overload / CORRECT");
        event.setPoints(120);
        event.setCreatedAt(Instant.parse("2026-06-01T10:01:00Z"));
        when(scoreService.getScoreEvents(1L)).thenReturn(List.of(event));

        List<ScoreEventResponse> events = scoreController.getScoreEvents(1L);

        assertNotNull(events);
        assertEquals(1, events.size());
        ScoreEventResponse response = events.get(0);
        assertEquals(9L, response.id());
        assertEquals(2L, response.playerId());
        assertEquals("Cell overload / CORRECT", response.reason());
        assertEquals(120, response.points());
        verify(scoreService, times(1)).getScoreEvents(1L);
    }

    @Test
    @DisplayName("NetworkCellController returns cells for player")
    void networkCellControllerGetCells() {
        GameSession session = session(1L, user(7L, "creator"));
        Player blue = player(2L, user(8L, "blue"), session, "Blue", 120, true);
        NetworkCell cell = cell(5L, session, blue, "Cell-A");
        when(cellService.getCells(1L, 2L)).thenReturn(List.of(cell));

        List<CellResponse> cells = cellController.getCells(1L, 2L);

        assertNotNull(cells);
        assertEquals(1, cells.size());
        CellResponse response = cells.get(0);
        assertEquals(5L, response.id());
        assertEquals(2L, response.playerId());
        assertEquals("WARNING", response.healthStatus());
        assertEquals("DRIFT", response.configStatus());
        verify(cellService, times(1)).getCells(1L, 2L);
    }

    @Test
    @DisplayName("NetworkCellController returns single cell by ID")
    void networkCellControllerGetCell() {
        GameSession session = session(1L, user(7L, "creator"));
        Player blue = player(2L, user(8L, "blue"), session, "Blue", 120, true);
        NetworkCell cell = cell(5L, session, blue, "Cell-A");
        when(cellService.getCell(1L, 5L)).thenReturn(cell);

        CellResponse response = cellController.getCell(1L, 5L);

        assertNotNull(response);
        assertEquals("Cell-A", response.cellName());
        verify(cellService, times(1)).getCell(1L, 5L);
    }

    @Test
    @DisplayName("SessionController creates session successfully")
    void sessionControllerCreateSession() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.createSession("Match", 7L, 300, "MEDIUM")).thenReturn(session);

        SessionResponse response = sessionController.createSession(
                new SessionController.CreateSessionRequest("Match", 7L, 300, "MEDIUM"));

        assertNotNull(response);
        assertEquals(1L, response.id());
        verify(sessionService, times(1)).createSession("Match", 7L, 300, "MEDIUM");
    }

    @Test
    @DisplayName("SessionController lists active sessions")
    void sessionControllerListSessions() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.listActiveSessions()).thenReturn(List.of(session));

        List<SessionResponse> sessions = sessionController.listSessions();

        assertNotNull(sessions);
        assertEquals(1, sessions.size());
        verify(sessionService, times(1)).listActiveSessions();
    }

    @Test
    @DisplayName("SessionController gets session by ID")
    void sessionControllerGetSession() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.getSession(1L)).thenReturn(session);

        SessionResponse response = sessionController.getSession(1L);

        assertNotNull(response);
        assertEquals("ABC123", response.sessionCode());
        verify(sessionService, times(1)).getSession(1L);
    }

    @Test
    @DisplayName("SessionController gets session by code")
    void sessionControllerGetByCode() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.getByCode("ABC123")).thenReturn(session);

        SessionResponse response = sessionController.getByCode("ABC123");

        assertNotNull(response);
        assertEquals(7L, response.createdByUserId());
        verify(sessionService, times(1)).getByCode("ABC123");
    }

    @Test
    @DisplayName("SessionController joins player to session")
    void sessionControllerJoinSession() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        Player blue = player(2L, creator, session, "Blue", 0, false);
        when(sessionService.joinSession(1L, 7L, "Blue")).thenReturn(blue);

        PlayerResponse response = sessionController.joinSession(1L,
                new SessionController.JoinSessionRequest(7L, "Blue"));

        assertNotNull(response);
        assertEquals("Blue", response.teamName());
        verify(sessionService, times(1)).joinSession(1L, 7L, "Blue");
    }

    @Test
    @DisplayName("SessionController marks player ready")
    void sessionControllerReady() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.markReady(1L, 2L)).thenReturn(session);

        SessionResponse response = sessionController.ready(1L,
                new SessionController.ReadyRequest(2L));

        assertNotNull(response);
        assertEquals("WAITING", response.status());
        verify(sessionService, times(1)).markReady(1L, 2L);
    }

    @Test
    @DisplayName("SessionController removes player from session")
    void sessionControllerLeave() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.leaveSession(1L, 2L)).thenReturn(session);

        SessionResponse response = sessionController.leave(1L,
                new SessionController.LeaveRequest(2L));

        assertNotNull(response);
        assertEquals(1L, response.id());
        verify(sessionService, times(1)).leaveSession(1L, 2L);
    }

    @Test
    @DisplayName("SessionController starts session")
    void sessionControllerStartSession() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        when(sessionService.startSession(1L)).thenReturn(session);

        SessionResponse response = sessionController.startSession(1L);

        assertNotNull(response);
        assertEquals("MEDIUM", response.difficulty());
        verify(sessionService, times(1)).startSession(1L);
    }

    @Test
    @DisplayName("SessionController gets players in session")
    void sessionControllerGetPlayers() {
        AppUser creator = user(7L, "creator");
        GameSession session = session(1L, creator);
        Player blue = player(2L, creator, session, "Blue", 0, false);
        when(sessionService.getPlayers(1L)).thenReturn(List.of(blue));

        List<PlayerResponse> players = sessionController.getPlayers(1L);

        assertNotNull(players);
        assertEquals(1, players.size());
        assertEquals(2L, players.get(0).id());
        verify(sessionService, times(1)).getPlayers(1L);
    }

    @Test
    @DisplayName("IncidentController lists incidents for player")
    void incidentControllerGetIncidents() {
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        IncidentResponse incident = new IncidentResponse(
                6L, 1L, 2L, 5L, "Cell overload", "HIGH", "OPEN", "Overload", createdAt, null);
        when(incidentService.getIncidents(1L, 2L, "OPEN")).thenReturn(List.of(incident));

        List<IncidentResponse> incidents = incidentController.getIncidents(1L, 2L, "OPEN");

        assertNotNull(incidents);
        assertEquals(1, incidents.size());
        assertEquals(incident, incidents.get(0));
        verify(incidentService, times(1)).getIncidents(1L, 2L, "OPEN");
    }

    @Test
    @DisplayName("IncidentController gets single incident by ID")
    void incidentControllerGetIncident() {
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        IncidentResponse incident = new IncidentResponse(
                6L, 1L, 2L, 5L, "Cell overload", "HIGH", "OPEN", "Overload", createdAt, null);
        when(incidentService.getIncident(1L, 6L)).thenReturn(incident);

        IncidentResponse response = incidentController.getIncident(1L, 6L);

        assertNotNull(response);
        assertEquals("Overload", response.description());
        verify(incidentService, times(1)).getIncident(1L, 6L);
    }

    @Test
    @DisplayName("IncidentController submits action for incident")
    void incidentControllerSubmitAction() {
        PlayerAction action = playerAction();
        when(incidentService.submitAction(1L, 6L, 2L, 4L)).thenReturn(action);

        PlayerActionResponse response = incidentController.submitAction(1L, 6L,
                new IncidentController.SubmitActionRequest(2L, 4L));

        assertNotNull(response);
        assertEquals("SUCCESS", response.result());
        verify(incidentService, times(1)).submitAction(1L, 6L, 2L, 4L);
    }

    @Test
    @DisplayName("IncidentController gets actions for incident")
    void incidentControllerGetIncidentActions() {
        PlayerAction action = playerAction();
        when(incidentService.getActionsForIncident(1L, 6L)).thenReturn(List.of(action));

        List<PlayerActionResponse> responses = incidentController.getIncidentActions(1L, 6L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(140, responses.get(0).pointsAwarded());
        verify(incidentService, times(1)).getActionsForIncident(1L, 6L);
    }

    @Test
    @DisplayName("UserController creates user successfully")
    void userControllerCreateUser() {
        AppUser user = user(3L, "ava");
        when(userService.createUser("ava", "PLAYER")).thenReturn(user);

        AppUser response = userController.createUser(
                new UserController.CreateUserRequest("ava", "PLAYER"));

        assertNotNull(response);
        assertEquals(3L, response.getId());
        verify(userService, times(1)).createUser("ava", "PLAYER");
    }

    @Test
    @DisplayName("UserController logs in user successfully")
    void userControllerLogin() {
        AppUser user = user(3L, "ava");
        when(userService.login("ava")).thenReturn(user);

        AppUser response = userController.login(new UserController.LoginRequest("ava"));

        assertNotNull(response);
        assertEquals("ava", response.getUsername());
        verify(userService, times(1)).login("ava");
    }

    @Test
    @DisplayName("UserController gets user by ID")
    void userControllerGetUser() {
        AppUser user = user(3L, "ava");
        when(userService.getUser(3L)).thenReturn(user);

        AppUser response = userController.getUser(3L);

        assertNotNull(response);
        assertEquals("PLAYER", response.getRole());
        verify(userService, times(1)).getUser(3L);
    }

    @Test
    @DisplayName("PlayerActionResponse maps the full action verdict shape")
    void playerActionResponseMapsVerdictShape() {
        PlayerAction playerAction = playerAction();
        Instant submittedAt = playerAction.getSubmittedAt();

        PlayerActionResponse response = PlayerActionResponse.from(playerAction);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals(2L, response.playerId());
        assertEquals(6L, response.incidentId());
        assertEquals(4L, response.actionId());
        assertEquals("SUCCESS", response.result());
        assertEquals(140, response.pointsAwarded());
        assertEquals(submittedAt, response.submittedAt());
    }

    @Test
    @DisplayName("SessionResponse forfeitedByPlayerId is null when not forfeited")
    void sessionResponseForfeitedByPlayerId() {
        GameSession session = session(1L, user(7L, "creator"));

        SessionResponse response = SessionResponse.from(session);

        assertNull(response.forfeitedByPlayerId());
    }

    // ...existing code...

    // Static helper methods to build test objects
    private static PlayerAction playerAction() {
        GameSession session = session(1L, user(7L, "creator"));
        Player player = player(2L, user(8L, "blue"), session, "Blue", 0, true);
        NetworkCell cell = cell(5L, session, player, "Cell-A");
        Incident incident = new Incident();
        incident.setId(6L);
        incident.setGameSession(session);
        incident.setPlayer(player);
        incident.setCell(cell);
        incident.setIncidentType("Cell overload");
        incident.setSeverity(Severity.HIGH);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription("Overload");
        PlayerAction playerAction = new PlayerAction();
        playerAction.setId(10L);
        playerAction.setPlayer(player);
        playerAction.setIncident(incident);
        playerAction.setAction(action(4L, "REBALANCE_TRAFFIC"));
        playerAction.setResult(ActionResult.SUCCESS);
        playerAction.setPointsAwarded(140);
        playerAction.setSubmittedAt(Instant.parse("2026-06-01T10:02:00Z"));
        return playerAction;
    }

    private static AppUser user(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole("PLAYER");
        return user;
    }

    private static GameSession session(Long id, AppUser creator) {
        GameSession session = new GameSession();
        session.setId(id);
        session.setSessionCode("ABC123");
        session.setName("Match");
        session.setStatus(SessionStatus.WAITING);
        session.setDurationSeconds(300);
        session.setDifficulty(Difficulty.MEDIUM);
        session.setCreatedByUser(creator);
        return session;
    }

    private static Player player(Long id, AppUser user, GameSession session, String team, int score, boolean ready) {
        Player player = new Player();
        player.setId(id);
        player.setUser(user);
        player.setGameSession(session);
        player.setTeamName(team);
        player.setScore(score);
        player.setReady(ready);
        return player;
    }

    private static NetworkCell cell(Long id, GameSession session, Player player, String name) {
        NetworkCell cell = new NetworkCell();
        cell.setId(id);
        cell.setGameSession(session);
        cell.setPlayer(player);
        cell.setCellName(name);
        cell.setSignalQuality(70.0);
        cell.setUserLoad(80.0);
        cell.setLatency(90.0);
        cell.setPacketLoss(3.0);
        cell.setAlarmCount(2);
        cell.setEnergyUsage(55.0);
        cell.setHealthStatus(HealthStatus.WARNING);
        cell.setConfigStatus(ConfigStatus.DRIFT);
        return cell;
    }

    private static Action action(Long id, String name) {
        Action action = new Action();
        action.setId(id);
        action.setActionName(name);
        action.setDescription("Action " + name);
        return action;
    }
}
