package com.oran.defender.controller;

import static com.oran.defender.controller.ControllerTestData.cell;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.GlobalExceptionHandler;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.NetworkCell.HealthStatus;
import com.oran.defender.service.NetworkCellService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NetworkCellController.class)
@Import(GlobalExceptionHandler.class)
class NetworkCellControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetworkCellService networkCellService;

    @Test
    void getCellsReturnsCellsForSession() throws Exception {
        when(networkCellService.getCells(11L))
                .thenReturn(List.of(cell(51L, "Cell A", HealthStatus.WARNING)));

        mockMvc.perform(get("/api/sessions/{sessionId}/cells", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(51))
                .andExpect(jsonPath("$[0].cellName").value("Cell A"))
                .andExpect(jsonPath("$[0].healthStatus").value("WARNING"));

        verify(networkCellService).getCells(11L);
    }

    @Test
    void getCellReturnsSingleCell() throws Exception {
        when(networkCellService.getCell(11L, 51L))
                .thenReturn(cell(51L, "Cell A", HealthStatus.GOOD));

        mockMvc.perform(get("/api/sessions/{sessionId}/cells/{cellId}", 11L, 51L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(51))
                .andExpect(jsonPath("$.signalQuality").value(87.5))
                .andExpect(jsonPath("$.healthStatus").value("GOOD"));
    }

    @Test
    void getCellMapsMissingCellToNotFound() throws Exception {
        when(networkCellService.getCell(11L, 99L))
                .thenThrow(new NotFoundException("Cell not found"));

        mockMvc.perform(get("/api/sessions/{sessionId}/cells/{cellId}", 11L, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cell not found"));
    }
}
