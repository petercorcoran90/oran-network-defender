package com.oran.defender.dto;

import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.NetworkCell.HealthStatus;

public record NetworkCellResponse(
        Long id,
        Long sessionId,
        Long playerId,
        String cellName,
        Double signalQuality,
        Double userLoad,
        Double latency,
        Double packetLoss,
        Integer alarmCount,
        Double energyUsage,
        HealthStatus healthStatus) {

    public static NetworkCellResponse from(NetworkCell cell) {
        return new NetworkCellResponse(
                cell.getId(),
                cell.getGameSession().getId(),
                cell.getPlayer().getId(),
                cell.getCellName(),
                cell.getSignalQuality(),
                cell.getUserLoad(),
                cell.getLatency(),
                cell.getPacketLoss(),
                cell.getAlarmCount(),
                cell.getEnergyUsage(),
                cell.getHealthStatus());
    }
}
