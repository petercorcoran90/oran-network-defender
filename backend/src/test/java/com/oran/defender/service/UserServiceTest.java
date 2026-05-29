package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private UserService userService;

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void createUser_savesAndReturnsUser() {
        AppUser saved = userWith(1L, "alice", "PLAYER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenReturn(saved);

        AppUser result = userService.createUser("alice", "PLAYER");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRole()).isEqualTo("PLAYER");
        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void createUser_throwsConflict_whenUsernameAlreadyExists() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new AppUser()));

        assertThatThrownBy(() -> userService.createUser("alice", "PLAYER"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username already taken");

        verify(userRepository, never()).save(any());
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void getUser_returnsUser_whenFound() {
        AppUser user = userWith(42L, "bob", "ADMIN");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        AppUser result = userService.getUser(42L);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getUsername()).isEqualTo("bob");
        assertThat(result.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void getUser_throwsNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUser userWith(Long id, String username, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
