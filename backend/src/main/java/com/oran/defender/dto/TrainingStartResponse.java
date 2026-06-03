package com.oran.defender.dto;

/** What a solo Training session returns: the (already active) session plus the player's id. */
public record TrainingStartResponse(SessionResponse session, Long playerId) {}
