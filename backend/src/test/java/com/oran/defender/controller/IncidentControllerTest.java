package com.oran.defender.controller;

import static com.oran.defender.controller.ControllerTestData.NOW;
import static com.oran.defender.controller.ControllerTestData.playerAction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.exception.GlobalExceptionHandler;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.service.IncidentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IncidentController.class)
@Import(GlobalExceptionHandler.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentService incidentService;

    @Test
    void getIncidentsPassesOptionalStatusFilter() throws Exception {
        when(incidentService.getIncidents(11L, "OPEN"))
                .thenReturn(List.of(incidentResponse(31L, "OPEN")));

        mockMvc.perform(get("/api/sessions/{sessionId}/incidents", 11L)
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(31))
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        verify(incidentService).getIncidents(11L, "OPEN");
    }

    @Test
    void getIncidentsPassesNullStatusWhenFilterIsOmitted() throws Exception {
        when(incidentService.getIncidents(11L, null))
                .thenReturn(List.of(incidentResponse(31L, "OPEN")));

        mockMvc.perform(get("/api/sessions/{sessionId}/incidents", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(31));

        verify(incidentService).getIncidents(11L, null);
    }

    @Test
    void getIncidentReturnsIncidentDetails() throws Exception {
        when(incidentService.getIncident(11L, 31L)).thenReturn(incidentResponse(31L, "OPEN"));

        mockMvc.perform(get("/api/sessions/{sessionId}/incidents/{incidentId}", 11L, 31L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.gameSessionId").value(11))
                .andExpect(jsonPath("$.cellId").value(51))
                .andExpect(jsonPath("$.incidentType").value("Latency spike"));
    }

    @Test
    void getIncidentMapsMissingIncidentToNotFound() throws Exception {
        when(incidentService.getIncident(11L, 99L))
                .thenThrow(new NotFoundException("Incident not found"));

        mockMvc.perform(get("/api/sessions/{sessionId}/incidents/{incidentId}", 11L, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident not found"));
    }

    @Test
    void submitActionReturnsPlayerAction() throws Exception {
        when(incidentService.submitAction(11L, 31L, 21L, 41L))
                .thenReturn(playerAction(61L, ActionResult.SUCCESS, 50));

        mockMvc.perform(post("/api/sessions/{sessionId}/incidents/{incidentId}/actions", 11L, 31L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":21,"actionId":41}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(61))
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.pointsAwarded").value(50));

        verify(incidentService).submitAction(11L, 31L, 21L, 41L);
    }

    @Test
    void submitActionRejectsMissingActionId() throws Exception {
        mockMvc.perform(post("/api/sessions/{sessionId}/incidents/{incidentId}/actions", 11L, 31L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":21}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(incidentService);
    }

    @Test
    void submitActionMapsInvalidActionToBadRequest() throws Exception {
        when(incidentService.submitAction(11L, 31L, 21L, 41L))
                .thenThrow(new InvalidActionException("Incident is not open"));

        mockMvc.perform(post("/api/sessions/{sessionId}/incidents/{incidentId}/actions", 11L, 31L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId":21,"actionId":41}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incident is not open"));
    }

    @Test
    void getIncidentActionsReturnsActions() throws Exception {
        when(incidentService.getActionsForIncident(11L, 31L))
                .thenReturn(List.of(playerAction(61L, ActionResult.PARTIAL, 10)));

        mockMvc.perform(get("/api/sessions/{sessionId}/incidents/{incidentId}/actions", 11L, 31L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(61))
                .andExpect(jsonPath("$[0].result").value("PARTIAL"))
                .andExpect(jsonPath("$[0].pointsAwarded").value(10));
    }

    private static IncidentResponse incidentResponse(Long id, String status) {
        return new IncidentResponse(
                id,
                11L,
                51L,
                "Latency spike",
                "HIGH",
                status,
                "Latency rose above threshold",
                NOW,
                null);
    }
}
