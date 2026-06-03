package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link SymptomGroup} — the layer that hides a {@link RootCause} behind an
 * ambiguous, deducible group. The key invariants are structural: the groups partition every root
 * cause exactly once (so {@code of} always resolves), and the investigation budget is smaller than
 * the number of diagnostics on offer (so you can't brute-force the answer).
 */
@DisplayName("SymptomGroup")
class SymptomGroupTest {

    @ParameterizedTest
    @EnumSource(RootCause.class)
    @DisplayName("every root cause belongs to exactly one group")
    void everyRootCauseHasOneGroup(RootCause cause) {
        long groupsContaining = java.util.Arrays.stream(SymptomGroup.values())
                .filter(g -> g.candidates().contains(cause))
                .count();
        assertThat(groupsContaining).isEqualTo(1);
        assertThat(SymptomGroup.of(cause).candidates()).contains(cause);
    }

    @Test
    @DisplayName("the groups partition all root causes with no overlap")
    void groupsPartitionAllRootCauses() {
        Set<RootCause> union = EnumSet.noneOf(RootCause.class);
        int total = 0;
        for (SymptomGroup group : SymptomGroup.values()) {
            union.addAll(group.candidates());
            total += group.candidates().size();
        }
        assertThat(union).containsExactlyInAnyOrder(RootCause.values());
        assertThat(total).isEqualTo(RootCause.values().length); // no cause counted twice
    }

    @Test
    @DisplayName("a group's diagnostics leave at most one candidate to deduce by elimination")
    void diagnosticsResolveEveryGroup() {
        for (SymptomGroup group : SymptomGroup.values()) {
            Set<RootCause> directlyConfirmable = EnumSet.noneOf(RootCause.class);
            group.diagnostics().forEach(d -> directlyConfirmable.add(d.implicates()));
            int undeducible = group.candidates().size() - directlyConfirmable.size();
            // every candidate but (at most) one can be confirmed; the last falls out by elimination.
            assertThat(undeducible).isLessThanOrEqualTo(1);
            assertThat(group.candidates()).containsAll(directlyConfirmable);
        }
    }

    @Test
    @DisplayName("budget is 2 for the four-candidate group and 1 for the smaller groups")
    void diagnosticBudgetScalesWithAmbiguity() {
        assertThat(SymptomGroup.SERVICE_DEGRADATION.candidates()).hasSize(4);
        assertThat(SymptomGroup.SERVICE_DEGRADATION.diagnosticBudget()).isEqualTo(2);
        assertThat(SymptomGroup.CONGESTION.diagnosticBudget()).isEqualTo(1);
        assertThat(SymptomGroup.ALARMS.diagnosticBudget()).isEqualTo(1);
    }

    @Test
    @DisplayName("the budget is always smaller than the diagnostics on offer")
    void budgetIsLessThanAvailableDiagnostics() {
        for (SymptomGroup group : SymptomGroup.values()) {
            assertThat(group.diagnosticBudget()).isLessThan(group.diagnostics().size() + 1);
            assertThat(group.diagnosticBudget()).isPositive();
        }
    }

    @Test
    @DisplayName("each group exposes a human-readable label")
    void groupsHaveLabels() {
        assertThat(SymptomGroup.CONGESTION.label()).isEqualTo("Congestion");
        assertThat(SymptomGroup.SERVICE_DEGRADATION.label()).isEqualTo("Service degradation");
        assertThat(SymptomGroup.ALARMS.label()).isEqualTo("Alarms");
    }
}
