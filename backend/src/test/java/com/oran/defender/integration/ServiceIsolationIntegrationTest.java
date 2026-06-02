package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.engine.RootCause;
import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.service.IncidentService;
import com.oran.defender.service.NetworkCellService;
import com.oran.defender.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("Service isolation and user behavior (MySQL Testcontainer)")
class ServiceIsolationIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private NetworkCellService networkCellService;
    @Autowired private IncidentService incidentService;
    @Autowired private AppUserRepository users;
    @Autowired private GameSessionRepository sessions;
    @Autowired private PlayerRepository players;
    @Autowired private NetworkCellRepository cells;
    @Autowired private IncidentRepository incidents;

    @Test
    @DisplayName("UserService creates, reuses login identities, rejects duplicates, and 404s missing ids")
    void userServiceBehavior() {
        AppUser created = userService.createUser("svc-user", "ADMIN");
        assertThat(created.getRole()).isEqualTo("ADMIN");

        assertThatThrownBy(() -> userService.createUser("svc-user", "PLAYER"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken");
        assertThat(userService.login("svc-user").getId()).isEqualTo(created.getId());

        AppUser auto = userService.login("svc-auto");
        assertThat(auto.getRole()).isEqualTo("PLAYER");
        assertThat(userService.getUser(auto.getId()).getUsername()).isEqualTo("svc-auto");
        assertThatThrownBy(() -> userService.getUser(999_999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("NetworkCellService scopes player-filtered reads to the requested session")
    void networkCellsAreScopedBySessionAndPlayer() {
        AppUser a = users.save(Fixtures.user("cell-scope-a"));
        AppUser b = users.save(Fixtures.user("cell-scope-b"));
        GameSession s1 = sessions.save(Fixtures.activeSession("CEL001", a));
        GameSession s2 = sessions.save(Fixtures.activeSession("CEL002", b));
        Player p1 = players.save(Fixtures.player(a, s1, "Blue"));
        Player p2 = players.save(Fixtures.player(b, s2, "Red"));
        NetworkCell c1 = cells.save(Fixtures.cell(s1, p1, "Cell-A"));
        cells.save(Fixtures.cell(s2, p2, "Cell-B"));

        assertThat(networkCellService.getCells(s1.getId(), p1.getId())).extracting(NetworkCell::getCellName)
                .containsExactly("Cell-A");
        assertThat(networkCellService.getCells(s2.getId(), p1.getId())).isEmpty();
        assertThat(networkCellService.getCells(s2.getId(), null)).extracting(NetworkCell::getCellName)
                .containsExactly("Cell-B");
        assertThat(networkCellService.getCell(s1.getId(), c1.getId()).getCellName()).isEqualTo("Cell-A");
        Long s2Id = s2.getId();
        Long c1Id = c1.getId();
        assertThatThrownBy(() -> networkCellService.getCell(s2Id, c1Id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("IncidentService scopes player-filtered reads to the requested session and validates statuses")
    void incidentsAreScopedBySessionAndPlayer() {
        AppUser a = users.save(Fixtures.user("inc-scope-a"));
        AppUser b = users.save(Fixtures.user("inc-scope-b"));
        GameSession s1 = sessions.save(Fixtures.activeSession("INC001", a));
        GameSession s2 = sessions.save(Fixtures.activeSession("INC002", b));
        Player p1 = players.save(Fixtures.player(a, s1, "Blue"));
        Player p2 = players.save(Fixtures.player(b, s2, "Red"));
        NetworkCell c1 = cells.save(Fixtures.cell(s1, p1, "Cell-A"));
        NetworkCell c2 = cells.save(Fixtures.cell(s2, p2, "Cell-B"));
        Incident i1 = incidents.save(Fixtures.openIncident(s1, p1, c1, "Cell overload", RootCause.CELL_OVERLOAD, Severity.HIGH));
        incidents.save(Fixtures.openIncident(s2, p2, c2, "Drift", RootCause.NEIGHBOUR_CONFIG_CHANGE, Severity.MEDIUM));

        assertThat(incidentService.getIncidents(s1.getId(), p1.getId(), "open")).extracting(IncidentResponse::id)
                .containsExactly(i1.getId());
        assertThat(incidentService.getIncidents(s2.getId(), p1.getId(), null)).isEmpty();
        assertThat(incidentService.getIncidents(s2.getId(), null, "OPEN")).hasSize(1);
        assertThat(incidentService.getIncident(s1.getId(), i1.getId()).id()).isEqualTo(i1.getId());
        Long incS2Id = s2.getId();
        Long i1Id = i1.getId();
        Long incS1Id = s1.getId();
        Long incP1Id = p1.getId();
        assertThatThrownBy(() -> incidentService.getIncident(incS2Id, i1Id))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> incidentService.getIncidents(incS1Id, incP1Id, "stale"))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("Unknown incident status");
    }
}
