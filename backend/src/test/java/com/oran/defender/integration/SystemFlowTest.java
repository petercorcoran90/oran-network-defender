package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.oran.defender.dto.ActionResponse;
import com.oran.defender.dto.CellResponse;
import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.dto.PlayerActionResponse;
import com.oran.defender.dto.PlayerResponse;
import com.oran.defender.dto.SessionResponse;
import com.oran.defender.model.AppUser;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The brief's mandatory full-flow system test, over real HTTP against a real MySQL container:
 * create session → two users join → both ready (→ ACTIVE) → the simulator ingests a cell + an
 * incident → a player submits the correct action → score and incident state change.
 *
 * <p>RANDOM_PORT + {@link TestRestTemplate} means this exercises the real controllers, JSON
 * serialisation, validation and persistence end-to-end — no mocks. The test only knows the root
 * cause because it plays the simulator's role when ingesting the incident; real clients never see
 * it (asserted below). This test is NOT {@code @Transactional}: each HTTP call commits, exactly
 * like production, and it uses its own users so it doesn't clash with the other integration tests.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Full game flow over HTTP (MySQL Testcontainer)")
class SystemFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("create → join×2 → ready → incident → correct action → score & state change")
    void fullFlow() {
        // 1. Two players identify (find-or-create login).
        Long user1 = login("ava").getId();
        Long user2 = login("ben").getId();

        // 2. Create a session — starts WAITING.
        ResponseEntity<SessionResponse> created = rest.postForEntity("/api/sessions",
                Map.of("name", "System test match", "createdByUserId", user1,
                        "durationSeconds", 300, "difficulty", "MEDIUM"),
                SessionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long sessionId = created.getBody().id();
        assertThat(created.getBody().status()).isEqualTo("WAITING");

        // 3. Both users join.
        Long player1 = join(sessionId, user1, "Blue").id();
        Long player2 = join(sessionId, user2, "Red").id();

        // 4. Ready up: still WAITING after one, ACTIVE once both are ready.
        assertThat(ready(sessionId, player1).status()).isEqualTo("WAITING");
        assertThat(ready(sessionId, player2).status()).isEqualTo("ACTIVE");

        // 5. The simulator ingests a cell for player1, then an OPEN incident on it.
        ResponseEntity<CellResponse[]> cells = rest.postForEntity(
                "/api/internal/sessions/" + sessionId + "/cells",
                Map.of("playerId", player1, "cells", List.of(
                        Map.of("cellName", "Cell-A", "signalQuality", 40.0, "userLoad", 95.0,
                                "latency", 120.0, "packetLoss", 8.0, "alarmCount", 5,
                                "energyUsage", 70.0, "healthStatus", "CRITICAL", "configStatus", "STABLE"))),
                CellResponse[].class);
        assertThat(cells.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long cellId = cells.getBody()[0].id();

        ResponseEntity<IncidentResponse> incident = rest.postForEntity(
                "/api/internal/sessions/" + sessionId + "/incidents",
                Map.of("playerId", player1, "cellId", cellId, "incidentType", "Cell overload",
                        "severity", "HIGH", "rootCause", "CELL_OVERLOAD",
                        "description", "Cell is overloaded"),
                IncidentResponse.class);
        assertThat(incident.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long incidentId = incident.getBody().id();

        // Security: the actual hidden root cause is never serialised as a field. The candidate
        // SET (both causes for the Congestion group) is exposed for the deduction board, but
        // nothing marks which one is real — so the answer stays hidden.
        String incidentJson = rest.getForObject(
                "/api/sessions/" + sessionId + "/incidents/" + incidentId, String.class);
        assertThat(incidentJson).doesNotContain("\"rootCause\"");
        assertThat(incidentJson).contains("CELL_OVERLOAD").contains("ROGUE_AUTOMATION"); // ambiguous set

        // 6. The player submits the correct remediation (REBALANCE_TRAFFIC fixes CELL_OVERLOAD).
        ResponseEntity<PlayerActionResponse> outcome = rest.postForEntity(
                "/api/sessions/" + sessionId + "/incidents/" + incidentId + "/actions",
                Map.of("playerId", player1, "actionId", actionId("REBALANCE_TRAFFIC")),
                PlayerActionResponse.class);
        assertThat(outcome.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(outcome.getBody().result()).isEqualTo("SUCCESS");
        assertThat(outcome.getBody().pointsAwarded()).isPositive();

        // 7. Score changed: the scoreboard shows player1 with the awarded points.
        ResponseEntity<PlayerResponse[]> scores = rest.getForEntity(
                "/api/sessions/" + sessionId + "/scores", PlayerResponse[].class);
        PlayerResponse p1 = Arrays.stream(scores.getBody())
                .filter(p -> p.id().equals(player1)).findFirst().orElseThrow();
        assertThat(p1.score()).isEqualTo(outcome.getBody().pointsAwarded());

        // 8. State changed: the incident is now RESOLVED.
        ResponseEntity<IncidentResponse[]> resolved = rest.getForEntity(
                "/api/sessions/" + sessionId + "/incidents?playerId=" + player1 + "&status=RESOLVED",
                IncidentResponse[].class);
        assertThat(resolved.getBody()).hasSize(1);
        assertThat(resolved.getBody()[0].id()).isEqualTo(incidentId);
    }

    // --- HTTP helpers ---

    private AppUser login(String username) {
        return rest.postForEntity("/api/users/login",
                Map.of("username", username), AppUser.class).getBody();
    }

    private PlayerResponse join(Long sessionId, Long userId, String team) {
        return rest.postForEntity("/api/sessions/" + sessionId + "/join",
                Map.of("userId", userId, "teamName", team), PlayerResponse.class).getBody();
    }

    private SessionResponse ready(Long sessionId, Long playerId) {
        return rest.postForEntity("/api/sessions/" + sessionId + "/ready",
                Map.of("playerId", playerId), SessionResponse.class).getBody();
    }

    private Long actionId(String name) {
        ActionResponse[] actions = rest.getForEntity("/api/actions", ActionResponse[].class).getBody();
        return Arrays.stream(actions).filter(a -> a.actionName().equals(name))
                .findFirst().orElseThrow().id();
    }
}
