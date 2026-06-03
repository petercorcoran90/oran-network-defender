package com.oran.defender.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A player's learned skills, keyed by their {@code AppUser} id and persisted across matches. Tracks
 * which remediation actions and which diagnostics they've been taught; the tier is derived from the
 * totals (see {@code SkillTier}). One row per user, created on first read.
 */
@Entity
@Table(name = "user_skills")
@Getter
@Setter
@NoArgsConstructor
public class UserSkill {

    @Id
    private Long userId;   // == AppUser.id

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_learned_actions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "action_name", length = 40)
    private Set<String> learnedActions = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_learned_diagnostics", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "diagnostic_type", length = 40)
    private Set<String> learnedDiagnostics = new HashSet<>();

    public int learnedCount() {
        return learnedActions.size() + learnedDiagnostics.size();
    }
}
