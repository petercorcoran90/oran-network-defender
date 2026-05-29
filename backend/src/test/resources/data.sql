-- Seed data for the 'test' profile (H2 only). Loaded by spring.sql.init AFTER Hibernate
-- creates the schema (defer-datasource-initialization=true).
--
-- Describes one ACTIVE head-to-head match — alice vs bob — where each player owns a
-- mirrored copy of the network: 2 cells and 2 open incidents (a CELL_OVERLOAD and a
-- FALSE_ALARM). player_actions and score_events start empty, so scoring tests begin at 0.
-- The session's ended_at is well in the future so the lazy timer won't expire it mid-test.

INSERT INTO users (id, username, role, created_at) VALUES
  (1, 'alice', 'PLAYER', CURRENT_TIMESTAMP),
  (2, 'bob',   'PLAYER', CURRENT_TIMESTAMP);

INSERT INTO game_sessions (id, session_code, name, status, duration_seconds, started_at, ended_at, created_by_user_id) VALUES
  (1, 'TEST01', 'Test Match', 'ACTIVE', 900, CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP), 1);

INSERT INTO players (id, user_id, game_session_id, team_name, score, joined_at) VALUES
  (1, 1, 1, 'alice', 0, CURRENT_TIMESTAMP),
  (2, 2, 1, 'bob',   0, CURRENT_TIMESTAMP);

INSERT INTO network_cells (id, game_session_id, player_id, cell_name, signal_quality, user_load, latency, packet_loss, alarm_count, energy_usage, health_status) VALUES
  (1, 1, 1, 'Cell-A', 80, 92, 180, 8, 5, 50, 'WARNING'),
  (2, 1, 1, 'Cell-B', 95, 20, 30,  0, 0, 40, 'GOOD'),
  (3, 1, 2, 'Cell-A', 80, 92, 180, 8, 5, 50, 'WARNING'),
  (4, 1, 2, 'Cell-B', 95, 20, 30,  0, 0, 40, 'GOOD');

INSERT INTO incidents (id, game_session_id, player_id, cell_id, incident_type, severity, status, description, root_cause, created_at, resolved_at) VALUES
  (1, 1, 1, 1, 'CELL_OVERLOAD', 'HIGH', 'OPEN', 'Cell-A user load is 92% with rising latency.',  'CELL_OVERLOAD', CURRENT_TIMESTAMP, NULL),
  (2, 1, 1, 2, 'FALSE_ALARM',   'LOW',  'OPEN', 'Transient alarm on Cell-B; metrics look healthy.', 'FALSE_ALARM',   CURRENT_TIMESTAMP, NULL),
  (3, 1, 2, 3, 'CELL_OVERLOAD', 'HIGH', 'OPEN', 'Cell-A user load is 92% with rising latency.',  'CELL_OVERLOAD', CURRENT_TIMESTAMP, NULL),
  (4, 1, 2, 4, 'FALSE_ALARM',   'LOW',  'OPEN', 'Transient alarm on Cell-B; metrics look healthy.', 'FALSE_ALARM',   CURRENT_TIMESTAMP, NULL);

INSERT INTO actions (id, action_name, description) VALUES
  (1, 'REBALANCE_TRAFFIC',       'Move load to neighbouring cells to reduce congestion.'),
  (2, 'RESTART_CELL',            'Restart a cell to clear transient faults.'),
  (3, 'ROLLBACK_CONFIG',         'Restore the previous known-good network configuration.'),
  (4, 'ROLLBACK_SOFTWARE',       'Revert a faulty software upgrade.'),
  (5, 'INCREASE_TRANSMIT_POWER', 'Increase radio power to improve weak signal conditions.'),
  (6, 'FILTER_ALARMS',           'Filter alarms to identify the primary fault during an alarm storm.'),
  (7, 'DISABLE_AUTOMATION',      'Disable automation that is causing harmful network changes.'),
  (8, 'ESCALATE',                'Escalate an unresolvable fault for specialist handling.'),
  (9, 'IGNORE',                  'Take no remediation action when the incident is a false alarm.');

-- Move identity counters past the seeded rows so tests that insert NEW entities don't
-- collide with the explicit IDs above.
ALTER TABLE users         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE game_sessions ALTER COLUMN id RESTART WITH 100;
ALTER TABLE players       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE network_cells ALTER COLUMN id RESTART WITH 100;
ALTER TABLE incidents     ALTER COLUMN id RESTART WITH 100;
ALTER TABLE actions       ALTER COLUMN id RESTART WITH 100;
