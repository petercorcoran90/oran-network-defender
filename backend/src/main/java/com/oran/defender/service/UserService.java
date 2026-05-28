package com.oran.defender.service;

import com.oran.defender.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    // TODO: inject UserRepository

    public User createUser(String username, String role) {
        // TODO: check username not already taken, persist, return
        throw new UnsupportedOperationException("Not implemented");
    }

    public User getUser(Long id) {
        // TODO: find by ID or throw 404
        throw new UnsupportedOperationException("Not implemented");
    }
}
