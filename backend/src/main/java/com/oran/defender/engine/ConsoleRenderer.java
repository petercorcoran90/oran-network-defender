package com.oran.defender.engine;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The emulated diagnostic console. Pure and stateless: it recognises a typed command as a
 * {@link DiagnosticType} and renders <b>realistic terminal output</b> for the player to read and
 * interpret — the game never prints "confirmed/ruled out", you deduce it from the output.
 *
 * <p>Crucially this is an <b>emulator</b>: it maps a recognised command to generated text. Nothing
 * is ever executed — there is no shell, no eval, no filesystem. The output depends only on the
 * (already-computed) {@link EvidenceResult}, so the hidden root cause is never echoed back.
 */
@Component
public class ConsoleRenderer {

    /** Recognise a typed command line as one of the diagnostics (ignoring its arguments). */
    public Optional<DiagnosticType> match(String input) {
        String n = normalise(input);
        return Arrays.stream(DiagnosticType.values())
                .filter(d -> n.equals(d.match()) || n.startsWith(d.match() + " "))
                .findFirst();
    }

    /** Recognise a typed command line as a remediation action (ignoring its arguments). */
    public Optional<ActionType> matchAction(String input) {
        String n = normalise(input);
        return Arrays.stream(ActionType.values())
                .filter(a -> n.equals(a.match()) || n.startsWith(a.match() + " "))
                .findFirst();
    }

    public static String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** True if the typed command supplies every required argument token (taught via {@code man}). */
    public boolean hasRequiredArgs(String input, String[] required) {
        String n = normalise(input);
        for (String token : required) {
            if (!n.contains(token.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /** Realistic output for a diagnostic: an anomaly when it CONFIRMS its hypothesis, clean otherwise. */
    public String render(DiagnosticType type, EvidenceResult result) {
        boolean hit = result == EvidenceResult.CONFIRMS;
        return switch (type) {
            case TRACE_TRANSPORT -> hit
                    ? "traceroute to o-ru-07 (10.42.7.7), 30 hops max\n"
                    + " 1  o-du-gw        0.2 ms\n"
                    + " 2  fronthaul-sw1  0.4 ms   14% loss\n"
                    + " 3  o-ru-07        2.8 ms   57% loss"
                    : "traceroute to o-ru-07 (10.42.7.7), 30 hops max\n"
                    + " 1  o-du-gw        0.2 ms\n"
                    + " 2  fronthaul-sw1  0.3 ms   0% loss\n"
                    + " 3  o-ru-07        0.9 ms   0% loss";
            case CHECK_NEIGHBOUR_CONFIG -> hit
                    ? "--- baseline\n+++ running\n@@ cell-07 @@\n"
                    + "- pci: 211\n+ pci: 207\n  neighbor-list changed 18 min ago by cm-batch-job"
                    : "--- baseline\n+++ running\nNo differences.";
            case CHECK_UPGRADE_HISTORY -> hit
                    ? "deploy/o-du\nREVISION  CHANGE-CAUSE\n4         o-du-l1:4.0.0 (22 min ago)\n"
                    + "3         o-du-l1:3.9.4\n! pods CrashLoopBackOff x7 since revision 4"
                    : "deploy/o-du\nREVISION  CHANGE-CAUSE\n7         o-du-l1:3.2.1 (41 days ago)\nrollout healthy.";
            case RADIO_SCAN -> hit
                    ? "cell-07 counters:\n  rsrp: -78 dBm   sinr: 2.1 dB (low)\n"
                    + "  neighbour PCI 207 on the same frequency -> PCI collision"
                    : "cell-07 counters:\n  rsrp: -71 dBm   sinr: 19.4 dB\n  no PCI conflicts detected";
            case INSPECT_AUTOMATION -> hit
                    ? "traffic-steering-xapp\n10:31 applied handover policy (batch 326)\n"
                    + "10:31 applied handover policy (batch 327)\n"
                    + "10:32 applied handover policy (batch 328)   <- 328 changes in 5 min"
                    : "traffic-steering-xapp\n10:05 reconcile: no action needed\nidle since 10:05 (no recent changes)";
            case CORRELATE_ALARMS -> hit
                    ? "40 active alarms on cell-07\n"
                    + "CRITICAL  LinkDown          (root)\n"
                    + "MAJOR     HighBER           -> correlates to LinkDown\n"
                    + "correlation score 0.92  (a real fault is being masked)"
                    : "1 active alarm on cell-07\n"
                    + "WARNING   ThresholdCrossed  (flapping x31, auto-cleared)\n"
                    + "KPIs nominal · correlation score 0.04";
        };
    }
}
