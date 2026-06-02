package com.oran.defender.controller;

import com.oran.defender.dto.ManualResponse;
import com.oran.defender.dto.UserSkillResponse;
import com.oran.defender.model.AppUser;
import com.oran.defender.service.ProgressionService;
import com.oran.defender.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ProgressionService progressionService;

    public UserController(UserService userService, ProgressionService progressionService) {
        this.userService = userService;
        this.progressionService = progressionService;
    }

    record CreateUserRequest(@NotBlank String username, @NotBlank String role) {}
    record LoginRequest(@NotBlank String username) {}

    // Register a new user (strict: 409 if the username already exists)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUser createUser(@Valid @RequestBody CreateUserRequest req) {
        return userService.createUser(req.username(), req.role());
    }

    // Log in by username, registering on first use (no "name taken" error)
    @PostMapping("/login")
    public AppUser login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req.username());
    }

    // Get user by ID
    @GetMapping("/{id}")
    public AppUser getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    // A player's learned skills + tier (field manual / progression)
    @GetMapping("/{id}/skills")
    public UserSkillResponse getSkills(@PathVariable Long id) {
        userService.getUser(id); // 404 if the user doesn't exist
        return UserSkillResponse.from(progressionService.getOrCreate(id));
    }

    // The player's field manual — full detail for the commands they've learned (only those)
    @GetMapping("/{id}/manual")
    public ManualResponse getManual(@PathVariable Long id) {
        userService.getUser(id); // 404 if the user doesn't exist
        return ManualResponse.from(progressionService.getOrCreate(id));
    }
}
