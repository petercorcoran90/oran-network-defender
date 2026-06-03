package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.oran.defender.dto.ConsoleResponse;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.RootCause;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.DiagnosticRun;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.DiagnosticRunRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.service.IncidentService;
import com.oran.defender.service.ProgressionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end of the investigation path against real MySQL with the real {@code ConsoleRenderer} +
 * {@code DiagnosticEvaluator}: a typed console command is recognised, runs the relevant diagnostic,
 * persists the evidence, teaches the diagnostic, and returns emulated terminal output — never the
 * hidden root cause. The unit tests mock the renderer; this tier proves the wiring. The incident's
 * cause is TRANSPORT_LINK_FAULT (the SERVICE_DEGRADATION group), so {@code traceroute} applies.
 * {@code @Transactional} rolls each test back for isolation.
 */
@SpringBootTest
@Transactional
@DisplayName("Diagnostic console (MySQL Testcontainer)")
class DiagnosticConsoleIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private IncidentService incidentService;
    @Autowired private ProgressionService progressionService;
    @Autowired private AppUserRepository users;
    @Autowired private GameSessionRepository sessions;
    @Autowired private PlayerRepository players;
    @Autowired private NetworkCellRepository cells;
    @Autowired private IncidentRepository incidents;
    @Autowired private DiagnosticRunRepository diagnosticRuns;

    private record Scenario(GameSession session, Player player, Incident incident, Long userId) {}

    private Scenario degradedTransport(String code) {
        return scenario(code, "Service degradation", RootCause.TRANSPORT_LINK_FAULT);
    }

    private Scenario overloaded(String code) {
        return scenario(code, "Cell overload", RootCause.CELL_OVERLOAD);
    }

    private Scenario scenario(String code, String type, RootCause cause) {
        AppUser user = users.save(Fixtures.user("investigator-" + code));
        GameSession session = sessions.save(Fixtures.activeSession(code, user));
        Player player = players.save(Fixtures.player(user, session, "Blue"));
        NetworkCell cell = cells.save(Fixtures.cell(session, player, "Cell-A"));
        Incident incident = incidents.save(Fixtures.openIncident(
                session, player, cell, type, cause, Severity.HIGH));
        return new Scenario(session, player, incident, user.getId());
    }

    @Test
    @DisplayName("a relevant console command runs the diagnostic, records evidence, and teaches it")
    void consoleRunsDiagnosticAndLearns() {
        Scenario s = degradedTransport("DIAG01");

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "traceroute o-ru-07");

        assertThat(res.recognised()).isTrue();
        assertThat(res.output()).isNotBlank();
        // the verdict must be deduced from the output — it is never spelled out
        assertThat(res.output()).doesNotContain("CONFIRMS", "RULES_OUT", "TRANSPORT_LINK_FAULT");

        // evidence persisted: traceroute confirms the (real) transport fault
        var run = diagnosticRuns.findByIncidentIdAndPlayerIdAndDiagnosticType(
                s.incident().getId(), s.player().getId(), DiagnosticType.TRACE_TRANSPORT.name()).orElseThrow();
        assertThat(run.getResult()).isEqualTo("CONFIRMS");

        // running it taught the diagnostic (persisted progression)
        assertThat(progressionService.hasLearnedDiagnostic(s.userId(), DiagnosticType.TRACE_TRANSPORT)).isTrue();
    }

    @Test
    @DisplayName("runDiagnostic is idempotent — re-running does not consume more budget or duplicate evidence")
    void runDiagnosticIsIdempotent() {
        Scenario s = degradedTransport("DIAG02");

        DiagnosticRun first = incidentService.runDiagnostic(
                s.session().getId(), s.incident().getId(), s.player().getId(), DiagnosticType.TRACE_TRANSPORT.name());
        DiagnosticRun again = incidentService.runDiagnostic(
                s.session().getId(), s.incident().getId(), s.player().getId(), DiagnosticType.TRACE_TRANSPORT.name());

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(diagnosticRuns.countByIncidentIdAndPlayerId(s.incident().getId(), s.player().getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("'man <command>' teaches the full command + its arguments, without running anything")
    void manTeachesArgumentsWithoutRunning() {
        Scenario s = degradedTransport("DIAG03");

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "man traceroute");

        assertThat(res.recognised()).isTrue();
        assertThat(res.output()).contains(DiagnosticType.TRACE_TRANSPORT.command()); // "traceroute o-ru"
        assertThat(diagnosticRuns.countByIncidentIdAndPlayerId(s.incident().getId(), s.player().getId()))
                .isZero(); // man is free and runs nothing
    }

    @Test
    @DisplayName("a diagnostic command without its required argument is rejected, not run")
    void missingRequiredArgIsNotRun() {
        Scenario s = degradedTransport("DIAG04");

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "traceroute"); // missing o-ru target

        assertThat(res.output()).contains("missing or invalid arguments");
        assertThat(diagnosticRuns.countByIncidentIdAndPlayerId(s.incident().getId(), s.player().getId()))
                .isZero();
    }

    // ---- the console can also apply remediations (same engine + scoring as the action button) ----

    @Test
    @DisplayName("a remediation command applies the fix and resolves the incident")
    void consoleAppliesRemediation() {
        Scenario s = overloaded("DIAG05"); // REBALANCE_TRAFFIC is the correct fix for CELL_OVERLOAD

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "rrmctl rebalance --cell o-ru-07");

        assertThat(res.recognised()).isTrue();
        assertThat(res.output()).contains("resolved");
        assertThat(incidents.findById(s.incident().getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    @DisplayName("a remediation command without its required argument is rejected, not applied")
    void consoleRemediationMissingArg() {
        Scenario s = overloaded("DIAG06");

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "rrmctl rebalance"); // missing --cell

        assertThat(res.output()).contains("missing or invalid arguments");
        assertThat(incidents.findById(s.incident().getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.OPEN); // nothing applied
    }

    @Test
    @DisplayName("a real command that probes an unrelated subsystem is free and has no bearing")
    void consoleUnrelatedCommand() {
        Scenario s = overloaded("DIAG07"); // CONGESTION group; alarm correlation belongs to ALARMS

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "fmcli list-alarms");

        assertThat(res.recognised()).isTrue();
        assertThat(res.output()).contains("no bearing on this incident");
        assertThat(diagnosticRuns.countByIncidentIdAndPlayerId(s.incident().getId(), s.player().getId()))
                .isZero(); // unrelated probe records nothing
    }

    @Test
    @DisplayName("'man' for a remediation command explains it without applying it")
    void consoleManForAction() {
        Scenario s = overloaded("DIAG08");

        ConsoleResponse res = incidentService.runConsoleCommand(
                s.session().getId(), s.incident().getId(), s.player().getId(), "man rrmctl rebalance");

        assertThat(res.recognised()).isTrue();
        assertThat(res.output()).contains("Remediation");
        assertThat(incidents.findById(s.incident().getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.OPEN);
    }
}
