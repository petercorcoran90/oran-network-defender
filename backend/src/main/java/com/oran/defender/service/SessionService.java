package com.oran.defender.service;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.game.GameInitializer;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.PlayerRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    /** Head-to-head is exactly two players. */
    static final int MAX_PLAYERS = 2;

    private static final int DEFAULT_DURATION_SECONDS = 300;
    // Ambiguous characters (0/O, 1/I) omitted so codes are easy to read out loud.
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final GameSessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final GameInitializer gameInitializer;
    private final SecureRandom random = new SecureRandom();

    public SessionService(GameSessionRepository sessionRepository,
                          PlayerRepository playerRepository,
                          AppUserRepository userRepository,
                          GameInitializer gameInitializer) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.gameInitializer = gameInitializer;
    }

    @Transactional
    public GameSession createSession(String name, Long createdByUserId, Integer durationSeconds) {
        AppUser creator = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        GameSession session = new GameSession();
        session.setName(name);
        session.setSessionCode(generateUniqueCode());
        session.setCreatedByUser(creator);
        session.setStatus(SessionStatus.WAITING);
        session.setDurationSeconds(durationSeconds != null ? durationSeconds : DEFAULT_DURATION_SECONDS);
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<GameSession> listActiveSessions() {
        return sessionRepository.findByStatusIn(List.of(SessionStatus.WAITING, SessionStatus.ACTIVE));
    }

    @Transactional
    public GameSession getSession(Long sessionId) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        return endIfExpired(session);
    }

    @Transactional
    public Player joinSession(Long sessionId, Long userId, String teamName) {
        GameSession session = getSession(sessionId);
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new ConflictException("Session is not accepting players");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        playerRepository.findByUserIdAndGameSessionId(userId, sessionId).ifPresent(existing -> {
            throw new ConflictException("User has already joined this session");
        });
        long playerCount = playerRepository.countByGameSessionId(sessionId);
        if (playerCount >= MAX_PLAYERS) {
            throw new ConflictException("Session is full");
        }

        Player player = new Player();
        player.setUser(user);
        player.setGameSession(session);
        player.setTeamName(teamName == null || teamName.isBlank() ? user.getUsername() : teamName);
        player.setScore(0);
        return playerRepository.save(player);
    }

    /**
     * Marks a player ready. Once both players in a full session are ready, the match
     * activates. (Clients run a short countdown off the resulting ACTIVE status.)
     */
    @Transactional
    public GameSession markReady(Long sessionId, Long playerId) {
        GameSession session = getSession(sessionId);
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new ConflictException("Match has already started");
        }
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        if (!player.getGameSession().getId().equals(sessionId)) {
            throw new InvalidActionException("Player is not part of this session");
        }
        player.setReady(true);
        playerRepository.save(player);

        List<Player> players = playerRepository.findByGameSessionIdOrderByScoreDesc(sessionId);
        if (players.size() == MAX_PLAYERS && players.stream().allMatch(Player::isReady)) {
            activate(session);
        }
        return session;
    }

    @Transactional
    public GameSession startSession(Long sessionId) {
        GameSession session = getSession(sessionId);
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new ConflictException("Session cannot be started");
        }
        if (playerRepository.countByGameSessionId(sessionId) < MAX_PLAYERS) {
            throw new ConflictException("Session needs " + MAX_PLAYERS + " players to start");
        }
        activate(session);
        return session;
    }

    @Transactional(readOnly = true)
    public List<Player> getPlayers(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NotFoundException("Session not found");
        }
        return playerRepository.findByGameSessionIdOrderByScoreDesc(sessionId);
    }

    /** Flip to ACTIVE, persist, then seed both players' networks + incidents. */
    private void activate(GameSession session) {
        transitionToActive(session);
        sessionRepository.save(session);
        gameInitializer.setUpNetwork(session,
                playerRepository.findByGameSessionIdOrderByScoreDesc(session.getId()));
    }

    private void transitionToActive(GameSession session) {
        Instant now = Instant.now();
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(now);
        session.setEndedAt(now.plusSeconds(session.getDurationSeconds()));
    }

    /** Lazy timer: an ACTIVE session whose end time has passed is flipped to ENDED on read. */
    private GameSession endIfExpired(GameSession session) {
        if (session.getStatus() == SessionStatus.ACTIVE
                && session.getEndedAt() != null
                && Instant.now().isAfter(session.getEndedAt())) {
            session.setStatus(SessionStatus.ENDED);
            sessionRepository.save(session);
        }
        return session;
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (sessionRepository.findBySessionCode(code).isPresent());
        return code;
    }
}
