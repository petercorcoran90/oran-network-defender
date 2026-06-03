package com.oran.defender.service;

import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.model.UserSkill;
import com.oran.defender.repository.UserSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks what each player has learned (across matches). Learning is earned through gameplay — the
 * gameplay services call {@code learnAction}/{@code learnDiagnostic} when the player is taught a
 * command; the client can't assert its own progress. A skills row is created lazily on first touch.
 */
@Service
public class ProgressionService {

    private final UserSkillRepository repository;

    public ProgressionService(UserSkillRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserSkill getOrCreate(Long userId) {
        return repository.findById(userId).orElseGet(() -> {
            UserSkill skill = new UserSkill();
            skill.setUserId(userId);
            return repository.save(skill);
        });
    }

    @Transactional
    public UserSkill learnAction(Long userId, ActionType action) {
        UserSkill skill = getOrCreate(userId);
        if (skill.getLearnedActions().add(action.name())) {
            repository.save(skill);
        }
        return skill;
    }

    @Transactional
    public UserSkill learnDiagnostic(Long userId, DiagnosticType diagnostic) {
        UserSkill skill = getOrCreate(userId);
        if (skill.getLearnedDiagnostics().add(diagnostic.name())) {
            repository.save(skill);
        }
        return skill;
    }

    @Transactional(readOnly = true)
    public boolean hasLearnedAction(Long userId, ActionType action) {
        return repository.findById(userId)
                .map(s -> s.getLearnedActions().contains(action.name()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasLearnedDiagnostic(Long userId, DiagnosticType diagnostic) {
        return repository.findById(userId)
                .map(s -> s.getLearnedDiagnostics().contains(diagnostic.name()))
                .orElse(false);
    }
}
