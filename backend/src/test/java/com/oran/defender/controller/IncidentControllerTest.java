package com.oran.defender.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.Action;
import com.oran.defender.model.DiagnosticRun;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.service.IncidentService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for submitting actions — the brief's emphasis on "negative tests for invalid
 * player actions". Covers the happy path, missing-field validation, and the service rejecting an
 * action (wrong state / unknown incident) mapped to the right HTTP status with a leak-free body.
 */
@WebMvcTest(IncidentController.class)
@DisplayName("IncidentController — submit action")
class IncidentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IncidentService incidentService;

    private static final String URL = "/api/sessions/1/incidents/3/actions";

    private PlayerAction sampleAction() {
        PlayerAction pa = new PlayerAction();
        pa.setId(5L);
        Player p = new Player();
        p.setId(2L);
        pa.setPlayer(p);
        Incident i = new Incident();
        i.setId(3L);
        pa.setIncident(i);
        Action a = new Action();
        a.setId(4L);
        pa.setAction(a);
        pa.setResult(ActionResult.SUCCESS);
        pa.setPointsAwarded(140);
        pa.setSubmittedAt(Instant.parse("2026-06-01T10:00:00Z"));
        return pa;
    }

    @Test
    @DisplayName("valid submission -> 200 with the engine's verdict")
    void submitValid() throws Exception {
        given(incidentService.submitAction(1L, 3L, 2L, 4L)).willReturn(sampleAction());

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2,\"actionId\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.pointsAwarded").value(140))
                .andExpect(jsonPath("$.playerId").value(2))
                .andExpect(jsonPath("$.incidentId").value(3))
                .andExpect(jsonPath("$.actionId").value(4));
    }

    @Test
    @DisplayName("missing playerId -> 400, service untouched")
    void missingPlayerId() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionId\":4}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(incidentService);
    }

    @Test
    @DisplayName("missing actionId -> 400, service untouched")
    void missingActionId() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(incidentService);
    }

    @Test
    @DisplayName("action not allowed in the current state -> 400 with the reason")
    void invalidAction() throws Exception {
        given(incidentService.submitAction(1L, 3L, 2L, 4L))
                .willThrow(new InvalidActionException("Incident is not open"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2,\"actionId\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Incident is not open"));
    }

    @Test
    @DisplayName("unknown incident -> 404")
    void unknownIncident() throws Exception {
        given(incidentService.submitAction(1L, 3L, 2L, 4L))
                .willThrow(new NotFoundException("Incident not found"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2,\"actionId\":4}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident not found"));
    }

    @Test
    @DisplayName("error body carries only status/error/message — no stack trace or internals")
    void errorBodyDoesNotLeak() throws Exception {
        given(incidentService.submitAction(1L, 3L, 2L, 4L))
                .willThrow(new InvalidActionException("This incident belongs to another player"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2,\"actionId\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    // ---- diagnostics (investigation) ----

    private static final String DIAG_URL = "/api/sessions/1/incidents/3/diagnostics";

    private DiagnosticRun sampleRun() {
        DiagnosticRun run = new DiagnosticRun();
        run.setId(9L);
        run.setDiagnosticType("TRACE_TRANSPORT");
        run.setResult("CONFIRMS");
        run.setImplicated("TRANSPORT_LINK_FAULT");
        return run;
    }

    @Test
    @DisplayName("POST diagnostics -> 200 with the evidence (finding, never the raw root cause)")
    void runDiagnostic() throws Exception {
        given(incidentService.runDiagnostic(1L, 3L, 2L, "TRACE_TRANSPORT")).willReturn(sampleRun());

        mvc.perform(post(DIAG_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2,\"diagnostic\":\"TRACE_TRANSPORT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostic").value("TRACE_TRANSPORT"))
                .andExpect(jsonPath("$.label").value("Trace transport link"))
                .andExpect(jsonPath("$.result").value("CONFIRMS"))
                .andExpect(jsonPath("$.finding").value("Transport link fault — confirmed."));
    }

    @Test
    @DisplayName("POST diagnostics without a diagnostic -> 400")
    void runDiagnosticMissingField() throws Exception {
        mvc.perform(post(DIAG_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(incidentService);
    }

    @Test
    @DisplayName("GET diagnostics lists the evidence gathered so far")
    void listDiagnostics() throws Exception {
        given(incidentService.getDiagnostics(1L, 3L, 2L)).willReturn(List.of(sampleRun()));

        mvc.perform(get(DIAG_URL).param("playerId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diagnostic").value("TRACE_TRANSPORT"))
                .andExpect(jsonPath("$[0].result").value("CONFIRMS"));
    }
}
