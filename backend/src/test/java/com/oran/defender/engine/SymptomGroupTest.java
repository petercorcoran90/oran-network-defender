package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Structural invariants for the symptom-group model: the groups must partition every root cause,
 * stay ambiguous (≥2 candidates), and their diagnostics must be internally consistent.
 */
class SymptomGroupTest {

    @ParameterizedTest
    @EnumSource(RootCause.class)
    @DisplayName("every root cause belongs to exactly one symptom group")
    void everyRootCauseInExactlyOneGroup(RootCause rootCause) {
        long groups = Arrays.stream(SymptomGroup.values())
                .filter(g -> g.candidates().contains(rootCause))
                .count();
        assertThat(groups).as("groups containing %s", rootCause).isEqualTo(1);
        assertThat(SymptomGroup.of(rootCause).candidates()).contains(rootCause);
    }

    @Test
    @DisplayName("the groups together cover all root causes, with no overlap")
    void groupsPartitionAllRootCauses() {
        Set<RootCause> seen = new HashSet<>();
        int total = 0;
        for (SymptomGroup g : SymptomGroup.values()) {
            seen.addAll(g.candidates());
            total += g.candidates().size();
        }
        assertThat(seen).containsExactlyInAnyOrder(RootCause.values());
        assertThat(total).as("no root cause appears in two groups").isEqualTo(RootCause.values().length);
    }

    @ParameterizedTest
    @EnumSource(SymptomGroup.class)
    @DisplayName("each group is genuinely ambiguous (2+ candidates) and lists diagnostics")
    void groupsAreAmbiguous(SymptomGroup group) {
        assertThat(group.candidates().size()).isGreaterThanOrEqualTo(2);
        assertThat(group.diagnostics()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(SymptomGroup.class)
    @DisplayName("a group's diagnostics only test for causes that belong to that group")
    void diagnosticsImplicateOwnGroup(SymptomGroup group) {
        for (DiagnosticType d : group.diagnostics()) {
            assertThat(group.candidates())
                    .as("%s implicates %s, which must be in group %s", d, d.implicates(), group)
                    .contains(d.implicates());
        }
    }

    @Test
    @DisplayName("the diagnostic budget is below the diagnostics available, forcing uncertainty")
    void diagnosticBudgetForcesUncertainty() {
        assertThat(SymptomGroup.CONGESTION.diagnosticBudget()).isEqualTo(1);
        assertThat(SymptomGroup.ALARMS.diagnosticBudget()).isEqualTo(1);
        assertThat(SymptomGroup.SERVICE_DEGRADATION.diagnosticBudget()).isEqualTo(2);
        // The 4-candidate group can't be fully eliminated within budget — you must reason + commit.
        assertThat(SymptomGroup.SERVICE_DEGRADATION.diagnosticBudget())
                .isLessThan(SymptomGroup.SERVICE_DEGRADATION.diagnostics().size());
    }

    @ParameterizedTest
    @EnumSource(SymptomGroup.class)
    @DisplayName("at most one candidate per group is the 'residual' (implicated by no diagnostic) "
            + "— so elimination yields a unique answer")
    void atMostOneResidualCandidate(SymptomGroup group) {
        Set<RootCause> implicated = new HashSet<>();
        group.diagnostics().forEach(d -> implicated.add(d.implicates()));
        long residual = group.candidates().stream().filter(c -> !implicated.contains(c)).count();
        assertThat(residual).isLessThanOrEqualTo(1);
    }
}
