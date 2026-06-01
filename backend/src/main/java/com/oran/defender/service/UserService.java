package com.oran.defender.service;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository userRepository;

    public UserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AppUser createUser(String username, String role) {
        userRepository.findByUsername(username).ifPresent(existing -> {
            throw new ConflictException("Username already taken");
        });
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Log in by username, registering on first use. With no passwords the username is the
     * identity, so re-entering a known name returns that same user (lets a player start a new
     * game without a "name taken" error). NOTE: without auth this also means anyone can claim
     * any username — real authentication is still a documented gap.
     */
    @Transactional
    public AppUser login(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setRole("PLAYER");
            return userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public AppUser getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
