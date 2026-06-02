package com.oran.defender.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.UserSkill;
import com.oran.defender.service.ProgressionService;
import com.oran.defender.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer tests for login/registration: happy path, validation, and the duplicate-name conflict. */
@WebMvcTest(UserController.class)
@DisplayName("UserController web layer")
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @MockBean
    private ProgressionService progressionService;

    private AppUser user(String username) {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setUsername(username);
        u.setRole("PLAYER");
        return u;
    }

    @Test
    @DisplayName("POST /api/users/login -> 200 with the (find-or-created) user")
    void loginValid() throws Exception {
        given(userService.login("ava")).willReturn(user("ava"));

        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ava\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("ava"));
    }

    @Test
    @DisplayName("POST /api/users/login with a blank username -> 400")
    void loginBlank() throws Exception {
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("POST /api/users without a role -> 400")
    void createMissingRole() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ava\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("POST /api/users with a taken username -> 409")
    void createDuplicate() throws Exception {
        given(userService.createUser("ava", "PLAYER"))
                .willThrow(new ConflictException("Username already taken"));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ava\",\"role\":\"PLAYER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    @Test
    @DisplayName("GET /api/users/{id}/skills -> 200 with learned skills + derived tier")
    void getSkills() throws Exception {
        given(userService.getUser(1L)).willReturn(user("ava"));
        UserSkill skill = new UserSkill();
        skill.setUserId(1L);
        skill.getLearnedActions().add("REBALANCE_TRAFFIC");
        given(progressionService.getOrCreate(1L)).willReturn(skill);

        mvc.perform(get("/api/users/1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("TRAINEE"))
                .andExpect(jsonPath("$.learned").value(1))
                .andExpect(jsonPath("$.total").value(15))
                .andExpect(jsonPath("$.learnedActions[0]").value("REBALANCE_TRAFFIC"));
    }

    @Test
    @DisplayName("GET skills for an unknown user -> 404")
    void getSkillsUnknownUser() throws Exception {
        given(userService.getUser(9L)).willThrow(new NotFoundException("User not found"));

        mvc.perform(get("/api/users/9/skills"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(progressionService);
    }
}
