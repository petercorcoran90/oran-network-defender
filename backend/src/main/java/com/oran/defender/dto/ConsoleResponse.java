package com.oran.defender.dto;

/**
 * The result of one console command: the line that was run, whether it was recognised, and the
 * emulated terminal output to print. Never contains the hidden root cause — the player reads the
 * output and deduces.
 */
public record ConsoleResponse(String command, boolean recognised, String output) {}
