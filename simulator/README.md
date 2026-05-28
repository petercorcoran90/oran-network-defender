# Python Network Simulator

Placeholder for the Python simulator service.

## Responsibility
Generate time-series metric changes for network cells and fire incident events to the Game API.

## Expected interface

POST metrics update to Game API every N seconds:
```json
{
  "sessionId": "1",
  "cellId": "cell-17",
  "metrics": { "userLoad": 92, "latency": 180, "packetLoss": 8, "alarmCount": 5 },
  "timestamp": "..."
}
```

POST incident event to Game API when an incident triggers:
```json
{
  "sessionId": "1",
  "incidentType": "CONFIG_CHANGE_DEGRADATION",
  "affectedCells": ["cell-17"],
  "evidenceFactors": ["NEIGHBOUR_CONFIG_CHANGED", "HIGH_PACKET_LOSS"],
  "rootCause": "NEIGHBOUR_CONFIG_ROLLBACK_NEEDED",
  "severity": "HIGH"
}
```

## To do
- [ ] Create `requirements.txt`
- [ ] Implement `simulator.py` with seed-controlled metric generation
- [ ] Add Dockerfile
- [ ] Wire up incident triggers from GAME_LOGIC.md incident types
- [ ] Add tests for output validity and seed repeatability
