package com.oran.defender;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndFetchesUser() throws Exception {
        long userId = createUser("alice_user", "PLAYER");

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.username").value("alice_user"))
                .andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void rejectsInvalidAndDuplicateUsers() throws Exception {
        mockMvc.perform(postJson("/api/users", """
                {"username":"","role":"PLAYER"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        createUser("duplicate_user", "PLAYER");

        mockMvc.perform(postJson("/api/users", """
                {"username":"duplicate_user","role":"PLAYER"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    @Test
    void createsSessionJoinsTwoPlayersAndAutoStarts() throws Exception {
        long creatorId = createUser("creator_user", "PLAYER");
        long opponentId = createUser("opponent_user", "PLAYER");
        long sessionId = createSession("Training Match", creatorId, 120);

        mockMvc.perform(postJson("/api/sessions/" + sessionId + "/join", """
                {"userId":%d,"teamName":"Blue"}
                """.formatted(creatorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamName").value("Blue"))
                .andExpect(jsonPath("$.score").value(0));

        mockMvc.perform(postJson("/api/sessions/" + sessionId + "/join", """
                {"userId":%d,"teamName":"Red"}
                """.formatted(opponentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamName").value("Red"));

        mockMvc.perform(get("/api/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.durationSeconds").value(120));

        mockMvc.perform(get("/api/sessions/{id}/players", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/sessions/{id}/scores", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void refusesToStartSessionUntilTwoPlayersHaveJoined() throws Exception {
        long creatorId = createUser("solo_creator", "PLAYER");
        long sessionId = createSession("Solo Match", creatorId, null);

        mockMvc.perform(postJson("/api/sessions/" + sessionId + "/join", """
                {"userId":%d}
                """.formatted(creatorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamName").value("solo_creator"));

        mockMvc.perform(post("/api/sessions/{id}/start", sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Session needs 2 players to start"));
    }

    private long createUser(String username, String role) throws Exception {
        MvcResult result = mockMvc.perform(postJson("/api/users", """
                {"username":"%s","role":"%s"}
                """.formatted(username, role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        return idFrom(result);
    }

    private long createSession(String name, long createdByUserId, Integer durationSeconds) throws Exception {
        String durationField = durationSeconds == null ? "null" : durationSeconds.toString();
        MvcResult result = mockMvc.perform(postJson("/api/sessions", """
                {"name":"%s","createdByUserId":%d,"durationSeconds":%s}
                """.formatted(name, createdByUserId, durationField)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sessionCode", matchesPattern("[A-Z2-9]{6}")))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andReturn();
        return idFrom(result);
    }

    private long idFrom(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private RequestBuilder postJson(String path, String json) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
