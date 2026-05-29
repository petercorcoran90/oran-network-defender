package com.oran.defender.controller;

import static com.oran.defender.controller.ControllerTestData.user;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.GlobalExceptionHandler;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void createUserReturnsCreatedUser() throws Exception {
        when(userService.createUser("alice", "PLAYER"))
                .thenReturn(user(1L, "alice", "PLAYER"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","role":"PLAYER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("PLAYER"));

        verify(userService).createUser("alice", "PLAYER");
    }

    @Test
    void createUserRejectsBlankUsername() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","role":"PLAYER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(userService);
    }

    @Test
    void createUserRejectsBlankRole() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","role":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("role must not be blank"));

        verifyNoInteractions(userService);
    }

    @Test
    void createUserMapsDuplicateUsernameToConflict() throws Exception {
        when(userService.createUser("alice", "PLAYER"))
                .thenThrow(new ConflictException("Username already taken"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","role":"PLAYER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    @Test
    void getUserReturnsUser() throws Exception {
        when(userService.getUser(1L)).thenReturn(user(1L, "alice", "PLAYER"));

        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"));

        verify(userService).getUser(1L);
    }

    @Test
    void getUserMapsMissingUserToNotFound() throws Exception {
        when(userService.getUser(99L)).thenThrow(new NotFoundException("User not found"));

        mockMvc.perform(get("/api/users/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
    }
}
