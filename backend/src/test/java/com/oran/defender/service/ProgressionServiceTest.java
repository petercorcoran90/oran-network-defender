package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.model.UserSkill;
import com.oran.defender.repository.UserSkillRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressionService unit tests")
class ProgressionServiceTest {

    @Mock private UserSkillRepository repository;

    private ProgressionService progressionService;

    private UserSkill skill;

    @BeforeEach
    void setUp() {
        progressionService = new ProgressionService(repository);
        skill = new UserSkill();
        skill.setUserId(7L);
    }

    @Test
    @DisplayName("getOrCreate returns the existing row without saving")
    void getOrCreate_existing() {
        when(repository.findById(7L)).thenReturn(Optional.of(skill));

        UserSkill result = progressionService.getOrCreate(7L);

        assertThat(result).isSameAs(skill);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreate creates and saves a new row on first touch")
    void getOrCreate_createsLazily() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        when(repository.save(any(UserSkill.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSkill result = progressionService.getOrCreate(7L);

        assertThat(result.getUserId()).isEqualTo(7L);
        verify(repository).save(any(UserSkill.class));
    }

    @Test
    @DisplayName("learnAction records a newly learned action and persists it")
    void learnAction_new() {
        when(repository.findById(7L)).thenReturn(Optional.of(skill));

        progressionService.learnAction(7L, ActionType.REBALANCE_TRAFFIC);

        assertThat(skill.getLearnedActions()).contains("REBALANCE_TRAFFIC");
        verify(repository).save(skill);
    }

    @Test
    @DisplayName("learnAction is idempotent — an already-known action is not saved again")
    void learnAction_alreadyKnown() {
        skill.getLearnedActions().add("REBALANCE_TRAFFIC");
        when(repository.findById(7L)).thenReturn(Optional.of(skill));

        progressionService.learnAction(7L, ActionType.REBALANCE_TRAFFIC);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("learnDiagnostic records a newly learned diagnostic and persists it")
    void learnDiagnostic_new() {
        when(repository.findById(7L)).thenReturn(Optional.of(skill));

        progressionService.learnDiagnostic(7L, DiagnosticType.TRACE_TRANSPORT);

        assertThat(skill.getLearnedDiagnostics()).contains("TRACE_TRANSPORT");
        verify(repository).save(skill);
    }

    @Test
    @DisplayName("hasLearnedAction reflects what the row contains")
    void hasLearnedAction() {
        skill.getLearnedActions().add("RESTART_CELL");
        when(repository.findById(7L)).thenReturn(Optional.of(skill));

        assertThat(progressionService.hasLearnedAction(7L, ActionType.RESTART_CELL)).isTrue();
        assertThat(progressionService.hasLearnedAction(7L, ActionType.REBALANCE_TRAFFIC)).isFalse();
    }

    @Test
    @DisplayName("hasLearnedDiagnostic defaults to false when the player has no skills row yet")
    void hasLearnedDiagnostic_noRow() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThat(progressionService.hasLearnedDiagnostic(7L, DiagnosticType.RADIO_SCAN)).isFalse();
    }
}
