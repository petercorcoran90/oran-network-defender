package com.oran.defender.service;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.MatchResult;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.MatchResultRepository;
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
    private final MatchResultRepository matchResultRepository;
    private final ProgressionService progressionService;
    private final SecureRandom random = new SecureRandom();

    public SessionService(GameSessionRepository sessionRepository,
                          PlayerRepository playerRepository,
                          AppUserRepository userRepository,
                          MatchResultRepository matchResultRepository,
                          ProgressionService progressionService) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.matchResultRepository = matchResultRepository;
        this.progressionService = progressionService;
    }

    /**
     * Solo Training mode: one player, no opponent, activates immediately. The difficulty is set
     * from the player's current tier (Trainee→EASY, Operator→MEDIUM, Engineer→HARD) so the session
     * is sized to their skill — they ramp up across sessions as they learn.
     */
    @Transactional
    public Player createTrainingSession(Long userId, Integer durationSeconds) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        var tier = com.oran.defender.engine.SkillTier.of(progressionService.getOrCreate(userId).learnedCount());

        GameSession session = new GameSession();
        session.setName(user.getUsername() + " — training");
        session.setSessionCode(generateUniqueCode());
        session.setCreatedByUser(user);
        session.setMode(GameSession.Mode.TRAINING);
        session.setStatus(SessionStatus.WAITING);
        session.setDurationSeconds(durationSeconds != null ? durationSeconds : DEFAULT_DURATION_SECONDS);
        session.setDifficulty(difficultyForTier(tier));
        sessionRepository.save(session);

        Player player = new Player();
        player.setUser(user);
        player.setGameSession(session);
        player.setTeamName(user.getUsername());
        player.setScore(0);
        player.setReady(true);
        player = playerRepository.save(player);

        transitionToActive(session);     // no second player / ready-check — start now
        sessionRepository.save(session);
        return player;
    }

    private GameSession.Difficulty difficultyForTier(com.oran.defender.engine.SkillTier tier) {
        return switch (tier) {
            case TRAINEE -> GameSession.Difficulty.EASY;
            case OPERATOR -> GameSession.Difficulty.MEDIUM;
            case ENGINEER -> GameSession.Difficulty.HARD;
        };
    }

    @Transactional
    public GameSession createSession(String name, Long createdByUserId, Integer durationSeconds, String difficulty) {
        AppUser creator = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        GameSession session = new GameSession();
        session.setName(name);
        session.setSessionCode(generateUniqueCode());
        session.setCreatedByUser(creator);
        session.setStatus(SessionStatus.WAITING);
        session.setDurationSeconds(durationSeconds != null ? durationSeconds : DEFAULT_DURATION_SECONDS);
        session.setDifficulty(parseDifficulty(difficulty));
        return sessionRepository.save(session);
    }

    private GameSession.Difficulty parseDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return GameSession.Difficulty.MEDIUM;
        }
        try {
            return GameSession.Difficulty.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidActionException("Unknown difficulty: " + value);
        }
    }

    @Transactional
    public List<GameSession> listActiveSessions() {
        // Lazily end any whose timer has elapsed so they drop out of the list (otherwise an
        // expired ACTIVE session lingers forever and the simulator keeps maintaining it).
        return sessionRepository.findByStatusIn(List.of(SessionStatus.WAITING, SessionStatus.ACTIVE)).stream()
                .map(this::endIfExpired)
                .filter(s -> s.getStatus() != SessionStatus.ENDED)
                .toList();
    }

    @Transactional
    public GameSession getSession(Long sessionId) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        return endIfExpired(session);
    }

    @Transactional
    public GameSession getByCode(String code) {
        GameSession session = sessionRepository.findBySessionCode(code.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("No match with that code"));
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
     * A player leaves the match — this ends the session for everyone, so the other player's
     * client sees it move to ENDED and is shown the result instead of being left in a dead game.
     */
    @Transactional
    public GameSession leaveSession(Long sessionId, Long playerId) {
        GameSession session = getSession(sessionId);
        if (session.getStatus() != SessionStatus.ENDED) {
            session.setStatus(SessionStatus.ENDED);
            session.setEndedAt(Instant.now());
            session.setForfeitedByPlayerId(playerId); // ragequit = automatic forfeit
            sessionRepository.save(session);
            recordResult(session, playerId);
        }
        return session;
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

    /**
     * Flip the match to ACTIVE. The network + incidents are seeded by the Python simulator,
     * which polls for ACTIVE sessions and POSTs them in — so a session is briefly empty until
     * the simulator picks it up.
     */
    private void activate(GameSession session) {
        transitionToActive(session);
        sessionRepository.save(session);
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
            recordResult(session, null); // timer expiry — winner decided by score
        }
        return session;
    }

    /**
     * Persist the match result once (idempotent per session). On a forfeit the non-leaver
     * wins regardless of score; otherwise the higher score wins. Only records real matches
     * that actually started with two players.
     */
    private void recordResult(GameSession session, Long forfeitLoserId) {
        if (session.getStartedAt() == null || matchResultRepository.existsByGameSessionId(session.getId())) {
            return;
        }
        List<Player> players = playerRepository.findByGameSessionIdOrderByScoreDesc(session.getId());
        if (players.size() < 2) {
            return;
        }
        // Don't pollute the high-score table with an early ragequit nobody had scored in.
        if (forfeitLoserId != null && players.get(0).getScore() == 0) {
            return;
        }
        Player winner;
        Player loser;
        if (forfeitLoserId != null) {
            loser = players.stream().filter(p -> p.getId().equals(forfeitLoserId)).findFirst().orElse(players.get(1));
            winner = players.stream().filter(p -> !p.getId().equals(forfeitLoserId)).findFirst().orElse(players.get(0));
        } else {
            winner = players.get(0); // sorted by score desc
            loser = players.get(1);
        }
        MatchResult result = new MatchResult();
        result.setGameSessionId(session.getId());
        result.setWinnerName(winner.getTeamName());
        result.setWinnerScore(winner.getScore());
        result.setLoserName(loser.getTeamName());
        result.setDifficulty(session.getDifficulty().name());
        result.setDurationSeconds(session.getDurationSeconds());
        result.setForfeit(forfeitLoserId != null);
        matchResultRepository.save(result);
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
