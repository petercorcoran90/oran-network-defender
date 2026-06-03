package com.oran.defender.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.dto.ManualResponse;
import com.oran.defender.dto.UserSkillResponse;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.UserSkill;
import com.oran.defender.service.ProgressionService;
import com.oran.defender.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserController unit tests")
class UserControllerTest {

    private UserController userController;
    private UserService userService;
    private ProgressionService progressionService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        progressionService = mock(ProgressionService.class);
        userController = new UserController(userService, progressionService);
    }

    @Test
    @DisplayName("createUser returns created user")
    void createUser_success() {
        AppUser user = buildUser("ava", "PLAYER");
        UserController.CreateUserRequest req =
                new UserController.CreateUserRequest("ava", "PLAYER");

        when(userService.createUser("ava", "PLAYER")).thenReturn(user);

        AppUser result = userController.createUser(req);

        assertNotNull(result);
        assertEquals("ava", result.getUsername());
        assertEquals("PLAYER", result.getRole());
        verify(userService, times(1)).createUser("ava", "PLAYER");
    }

    @Test
    @DisplayName("createUser throws ConflictException when username already taken")
    void createUser_duplicate() {
        UserController.CreateUserRequest req =
                new UserController.CreateUserRequest("ava", "PLAYER");

        when(userService.createUser("ava", "PLAYER"))
                .thenThrow(new ConflictException("Username already taken"));

        assertThrows(ConflictException.class, () -> userController.createUser(req));
    }

    @Test
    @DisplayName("login returns existing user")
    void login_success() {
        AppUser user = buildUser("ava", "PLAYER");
        UserController.LoginRequest req = new UserController.LoginRequest("ava");

        when(userService.login("ava")).thenReturn(user);

        AppUser result = userController.login(req);

        assertNotNull(result);
        assertEquals("ava", result.getUsername());
        verify(userService, times(1)).login("ava");
    }

    @Test
    @DisplayName("login creates user on first use")
    void login_firstTime() {
        AppUser newUser = buildUser("newbie", "PLAYER");
        UserController.LoginRequest req = new UserController.LoginRequest("newbie");

        when(userService.login("newbie")).thenReturn(newUser);

        AppUser result = userController.login(req);

        assertNotNull(result);
        assertEquals("newbie", result.getUsername());
    }

    @Test
    @DisplayName("getUser returns user by id")
    void getUser_success() {
        AppUser user = buildUser("ava", "PLAYER");

        when(userService.getUser(1L)).thenReturn(user);

        AppUser result = userController.getUser(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(userService, times(1)).getUser(1L);
    }

    @Test
    @DisplayName("getUser throws NotFoundException when user does not exist")
    void getUser_notFound() {
        when(userService.getUser(99L)).thenThrow(new NotFoundException("User not found"));

        assertThrows(NotFoundException.class, () -> userController.getUser(99L));
    }

    @Test
    @DisplayName("getSkills returns the player's learned skills and derived tier")
    void getSkills_success() {
        UserSkill skill = new UserSkill();
        skill.setUserId(1L);
        skill.getLearnedActions().add("REBALANCE_TRAFFIC");
        skill.getLearnedDiagnostics().add("TRACE_TRANSPORT");
        when(userService.getUser(1L)).thenReturn(buildUser("ava", "PLAYER"));
        when(progressionService.getOrCreate(1L)).thenReturn(skill);

        UserSkillResponse result = userController.getSkills(1L);

        assertEquals(1L, result.userId());
        assertEquals(2, result.learned());
        assertEquals("TRAINEE", result.tier()); // 2 learned -> TRAINEE
        assertEquals(14, result.total()); // 8 learnable fixes + 6 diagnostics (Restart Cell is a trap, not counted)
        assertTrue(result.learnedActions().contains("REBALANCE_TRAFFIC"));
        assertTrue(result.learnedDiagnostics().contains("TRACE_TRANSPORT"));
        verify(progressionService, times(1)).getOrCreate(1L);
    }

    @Test
    @DisplayName("getSkills 404s (via the service) when the user does not exist")
    void getSkills_userNotFound() {
        when(userService.getUser(99L)).thenThrow(new NotFoundException("User not found"));

        assertThrows(NotFoundException.class, () -> userController.getSkills(99L));
    }

    @Test
    @DisplayName("getManual returns full detail only for the learned commands")
    void getManual_onlyLearnedCommands() {
        UserSkill skill = new UserSkill();
        skill.setUserId(1L);
        skill.getLearnedDiagnostics().add("TRACE_TRANSPORT");
        when(userService.getUser(1L)).thenReturn(buildUser("ava", "PLAYER"));
        when(progressionService.getOrCreate(1L)).thenReturn(skill);

        ManualResponse result = userController.getManual(1L);

        assertEquals(1, result.diagnostics().size());
        assertEquals("traceroute o-ru", result.diagnostics().get(0).command());
        assertTrue(result.actions().isEmpty()); // no actions learned yet
        assertEquals(6, result.diagnosticsTotal()); // all six diagnostics exist as targets
        assertEquals(8, result.actionsTotal()); // 8 fixable faults; Restart Cell is a trap, not learnable
    }

    private AppUser buildUser(String username, String role) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
