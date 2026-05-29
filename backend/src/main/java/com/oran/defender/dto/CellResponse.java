package com.oran.defender.dto;

import com.oran.defender.model.NetworkCell;

/** A network cell's current metrics, with the owning player's id (head-to-head). */
public record CellResponse(
        Long id,
        Long playerId,
        String cellName,
        double signalQuality,
        double userLoad,
        double latency,
        double packetLoss,
        int alarmCount,
        double energyUsage,
        String healthStatus
) {
    public static CellResponse from(NetworkCell c) {
        return new CellResponse(
                c.getId(),
                c.getPlayer().getId(),
                c.getCellName(),
                c.getSignalQuality(),
                c.getUserLoad(),
                c.getLatency(),
                c.getPacketLoss(),
                c.getAlarmCount(),
                c.getEnergyUsage(),
                c.getHealthStatus().name());
    }
}
