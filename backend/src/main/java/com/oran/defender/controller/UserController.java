package com.oran.defender.controller;

import com.oran.defender.model.AppUser;
import com.oran.defender.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    record CreateUserRequest(@NotBlank String username, @NotBlank String role) {}

    // Register a new user
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUser createUser(@Valid @RequestBody CreateUserRequest req) {
        return userService.createUser(req.username(), req.role());
    }

    // Get user by ID
    @GetMapping("/{id}")
    public AppUser getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }
}
