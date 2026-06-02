package com.oran.defender.dto;

import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.EvidenceResult;
import com.oran.defender.model.DiagnosticRun;

/**
 * The evidence a diagnostic produced, for the client. Carries the diagnostic's own hypothesis and
 * whether it was confirmed/ruled out — never the incident's actual hidden root cause.
 */
public record DiagnosticResponse(
        String diagnostic,
        String label,
        String result,
        String finding
) {
    public static DiagnosticResponse from(DiagnosticRun run) {
        DiagnosticType type = DiagnosticType.valueOf(run.getDiagnosticType());
        boolean confirmed = EvidenceResult.valueOf(run.getResult()) == EvidenceResult.CONFIRMS;
        String finding = type.hypothesis() + (confirmed ? " — confirmed." : " — ruled out.");
        return new DiagnosticResponse(run.getDiagnosticType(), type.label(), run.getResult(), finding);
    }
}
