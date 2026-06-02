package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oran.defender.dto.ConsoleResponse;
import com.oran.defender.engine.RootCause;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.DiagnosticRun;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.DiagnosticRunRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.service.IncidentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration of the investigation flow against real MySQL: diagnostics record evidence, are
 * idempotent, must match the incident's symptom group, are owner/state-guarded, and erode the
 * response-time bonus when the player later fixes the incident.
 */
@SpringBootTest
@Transactional
@DisplayName("Diagnostics / investigation (MySQL Testcontainer)")
class DiagnosticIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private IncidentService incidentService;
    @Autowired private AppUserRepository users;
    @Autowired private GameSessionRepository sessions;
    @Autowired private PlayerRepository players;
    @Autowired private NetworkCellRepository cells;
    @Autowired private IncidentRepository incidents;
    @Autowired private DiagnosticRunRepository diagnostics;
    @Autowired private ActionRepository actions;

    private Long actionId(String name) {
        return actions.findByActionName(name).orElseThrow().getId();
    }

    private record Scene(GameSession session, Player player, Incident incident) {}

    private Scene scene(String code, String user, RootCause rootCause, String cellName) {
        AppUser u = users.save(Fixtures.user(user));
        GameSession s = sessions.save(Fixtures.activeSession(code, u));
        Player p = players.save(Fixtures.player(u, s, "Blue"));
        NetworkCell c = cells.save(Fixtures.cell(s, p, cellName));
        Incident i = incidents.save(Fixtures.openIncident(s, p, c, "Degraded cell", rootCause, Severity.HIGH));
        return new Scene(s, p, i);
    }

    @Test
    @DisplayName("a diagnostic records evidence (rules out a non-matching hypothesis)")
    void recordsEvidence() {
        Scene sc = scene("DIA001", "dia-a", RootCause.CELL_OVERLOAD, "Cell-A");

        // CONGESTION group's diagnostic tests for ROGUE_AUTOMATION; the cause is CELL_OVERLOAD.
        DiagnosticRun run = incidentService.runDiagnostic(
                sc.session().getId(), sc.incident().getId(), sc.player().getId(), "INSPECT_AUTOMATION");

        assertThat(run.getResult()).isEqualTo("RULES_OUT");
        assertThat(run.getImplicated()).isEqualTo("ROGUE_AUTOMATION");
        assertThat(diagnostics.countByIncidentIdAndPlayerId(sc.incident().getId(), sc.player().getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("re-running the same diagnostic is idempotent (no second row)")
    void idempotent() {
        Scene sc = scene("DIA002", "dia-b", RootCause.CELL_OVERLOAD, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        DiagnosticRun first = incidentService.runDiagnostic(s, i, p, "INSPECT_AUTOMATION");
        DiagnosticRun again = incidentService.runDiagnostic(s, i, p, "INSPECT_AUTOMATION");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(diagnostics.countByIncidentIdAndPlayerId(i, p)).isEqualTo(1);
    }

    @Test
    @DisplayName("a confirming diagnostic, then the matching fix, resolves the incident")
    void confirmThenFix() {
        Scene sc = scene("DIA003", "dia-c", RootCause.TRANSPORT_LINK_FAULT, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        DiagnosticRun run = incidentService.runDiagnostic(s, i, p, "TRACE_TRANSPORT");
        assertThat(run.getResult()).isEqualTo("CONFIRMS");

        // ESCALATE is the correct fix for a transport link fault.
        var outcome = incidentService.submitAction(s, i, p, actionId("ESCALATE"));
        assertThat(outcome.getResult()).isEqualTo(ActionResult.SUCCESS);
    }

    @Test
    @DisplayName("a diagnostic from the wrong symptom group is rejected")
    void wrongGroupRejected() {
        Scene sc = scene("DIA004", "dia-d", RootCause.CELL_OVERLOAD, "Cell-A");
        // TRACE_TRANSPORT belongs to SERVICE_DEGRADATION, not the CONGESTION incident.
        assertThatThrownBy(() -> incidentService.runDiagnostic(
                sc.session().getId(), sc.incident().getId(), sc.player().getId(), "TRACE_TRANSPORT"))
                .isInstanceOf(InvalidActionException.class);
    }

    @Test
    @DisplayName("running a diagnostic costs points off the eventual fix (accuracy beats spam)")
    void diagnosticsCostPoints() {
        AppUser u = users.save(Fixtures.user("dia-e"));
        GameSession s = sessions.save(Fixtures.activeSession("DIA005", u));
        Player p = players.save(Fixtures.player(u, s, "Blue"));
        NetworkCell cA = cells.save(Fixtures.cell(s, p, "Cell-A"));
        NetworkCell cB = cells.save(Fixtures.cell(s, p, "Cell-B"));
        Incident noInvestigation = incidents.save(
                Fixtures.openIncident(s, p, cA, "Degraded cell", RootCause.CELL_OVERLOAD, Severity.HIGH));
        Incident investigated = incidents.save(
                Fixtures.openIncident(s, p, cB, "Degraded cell", RootCause.CELL_OVERLOAD, Severity.HIGH));

        // Fix one immediately; investigate the other (1 diagnostic, -15 pts) then fix it.
        int fast = incidentService.submitAction(
                s.getId(), noInvestigation.getId(), p.getId(), actionId("REBALANCE_TRAFFIC")).getPointsAwarded();
        incidentService.runDiagnostic(s.getId(), investigated.getId(), p.getId(), "INSPECT_AUTOMATION");
        int afterDiag = incidentService.submitAction(
                s.getId(), investigated.getId(), p.getId(), actionId("REBALANCE_TRAFFIC")).getPointsAwarded();

        assertThat(fast).isPositive();
        assertThat(afterDiag).isPositive().isLessThan(fast); // the diagnostic cost real points
    }

    @Test
    @DisplayName("a player cannot run diagnostics on another player's incident")
    void cannotDiagnoseAnotherPlayersIncident() {
        AppUser owner = users.save(Fixtures.user("dia-owner"));
        AppUser intruder = users.save(Fixtures.user("dia-intruder"));
        GameSession s = sessions.save(Fixtures.activeSession("DIA006", owner));
        Player p1 = players.save(Fixtures.player(owner, s, "Blue"));
        Player p2 = players.save(Fixtures.player(intruder, s, "Red"));
        NetworkCell c = cells.save(Fixtures.cell(s, p1, "Cell-A"));
        Incident inc = incidents.save(
                Fixtures.openIncident(s, p1, c, "Degraded cell", RootCause.CELL_OVERLOAD, Severity.HIGH));

        assertThatThrownBy(() -> incidentService.runDiagnostic(
                s.getId(), inc.getId(), p2.getId(), "INSPECT_AUTOMATION"))
                .isInstanceOf(InvalidActionException.class);
    }

    @Test
    @DisplayName("the per-incident diagnostic budget is enforced (can't test everything)")
    void budgetEnforced() {
        // Service-degradation has 4 diagnostics but a budget of 2.
        Scene sc = scene("DIA007", "dia-f", RootCause.TRANSPORT_LINK_FAULT, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        incidentService.runDiagnostic(s, i, p, "TRACE_TRANSPORT");        // 1
        incidentService.runDiagnostic(s, i, p, "CHECK_NEIGHBOUR_CONFIG"); // 2 — budget used

        assertThatThrownBy(() -> incidentService.runDiagnostic(s, i, p, "CHECK_UPGRADE_HISTORY"))
                .isInstanceOf(InvalidActionException.class);

        // …but re-running an already-run diagnostic is still allowed (idempotent, no extra charge).
        assertThat(incidentService.runDiagnostic(s, i, p, "TRACE_TRANSPORT")).isNotNull();
    }

    // ---- console ----

    @Test
    @DisplayName("console: help lists the incident's authentic commands")
    void consoleHelp() {
        Scene sc = scene("DIA008", "dia-g", RootCause.TRANSPORT_LINK_FAULT, "Cell-A");
        ConsoleResponse r = incidentService.runConsoleCommand(
                sc.session().getId(), sc.incident().getId(), sc.player().getId(), "help");
        assertThat(r.recognised()).isTrue();
        assertThat(r.output()).contains("traceroute o-ru").contains("netconf get-config");
    }

    @Test
    @DisplayName("console: a relevant command runs the diagnostic and prints realistic output")
    void consoleRunsDiagnostic() {
        Scene sc = scene("DIA009", "dia-h", RootCause.TRANSPORT_LINK_FAULT, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        ConsoleResponse r = incidentService.runConsoleCommand(s, i, p, "traceroute o-ru-07");

        assertThat(r.recognised()).isTrue();
        assertThat(r.output()).contains("loss");                                   // reads like traceroute
        assertThat(diagnostics.countByIncidentIdAndPlayerId(i, p)).isEqualTo(1);    // recorded + charged
    }

    @Test
    @DisplayName("console: unknown input is safe and free (nothing executed, no charge)")
    void consoleUnknownCommand() {
        Scene sc = scene("DIA010", "dia-i", RootCause.CELL_OVERLOAD, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        ConsoleResponse r = incidentService.runConsoleCommand(s, i, p, "rm -rf /");

        assertThat(r.recognised()).isFalse();
        assertThat(r.output()).contains("command not found");
        assertThat(diagnostics.countByIncidentIdAndPlayerId(i, p)).isZero();
    }

    @Test
    @DisplayName("console: a command for an unrelated subsystem is recognised but free")
    void consoleIrrelevantCommand() {
        // CONGESTION's only relevant diagnostic is INSPECT_AUTOMATION — traceroute doesn't apply.
        Scene sc = scene("DIA011", "dia-j", RootCause.CELL_OVERLOAD, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        ConsoleResponse r = incidentService.runConsoleCommand(s, i, p, "traceroute o-ru-07");

        assertThat(r.recognised()).isTrue();
        assertThat(r.output()).contains("no bearing on this incident");
        assertThat(diagnostics.countByIncidentIdAndPlayerId(i, p)).isZero();
    }

    @Test
    @DisplayName("console: a remediation command applies the fix (and resolves a correct one)")
    void consoleAppliesFix() {
        Scene sc = scene("DIA013", "con-x", RootCause.CELL_OVERLOAD, "Cell-A");
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        // rrmctl rebalance is the correct fix for CELL_OVERLOAD.
        ConsoleResponse r = incidentService.runConsoleCommand(s, i, p, "rrmctl rebalance --cell o-ru-07");

        assertThat(r.recognised()).isTrue();
        assertThat(r.output()).contains("resolved");
        assertThat(incidents.findById(i).orElseThrow().getStatus())
                .isEqualTo(com.oran.defender.model.Incident.IncidentStatus.RESOLVED);
    }

    @Test
    @DisplayName("console: relevant commands still respect the per-incident budget")
    void consoleRespectsBudget() {
        Scene sc = scene("DIA012", "dia-k", RootCause.TRANSPORT_LINK_FAULT, "Cell-A"); // budget 2
        Long s = sc.session().getId(), i = sc.incident().getId(), p = sc.player().getId();

        incidentService.runConsoleCommand(s, i, p, "traceroute o-ru");
        incidentService.runConsoleCommand(s, i, p, "netconf get-config o-du");

        assertThatThrownBy(() -> incidentService.runConsoleCommand(s, i, p, "kubectl rollout history deploy/o-du"))
                .isInstanceOf(InvalidActionException.class);
    }
}
