"""
Locust load test — 100 "players", each running a solo TRAINING game.

Purpose: drive enough load on the backend that the HorizontalPodAutoscaler scales the backend
Deployment up (2 -> up to 5 pods), then back down once the load stops. The backend is stateless
(all state in MySQL), so horizontal scaling is transparent to players.

Each virtual user logs in as a unique player, starts a training session, then loops on the same
REST calls a real client makes — polling incidents/cells/scores and running console commands
(the heavier path: real engine evaluation + DB writes).

--- Run it -------------------------------------------------------------------
1. Point at the in-cluster backend:
       kubectl -n oran port-forward svc/backend-service 8080:8080
2. In another terminal, watch the autoscaler + pods:
       kubectl -n oran get hpa,pods -w
3. Start Locust (web UI on http://localhost:8089) and ramp to 100 users:
       locust -f load-test/locustfile.py --host http://localhost:8080
   ...or headless (no UI):
       locust -f load-test/locustfile.py --host http://localhost:8080 \
              --headless -u 100 -r 10 -t 5m
"""
import random
import uuid

from locust import HttpUser, task, between

# Mostly recognised commands, so the backend runs the real console/engine path.
COMMANDS = [
    "help",
    "man kubectl logs",
    "kubectl logs deploy/traffic-steering",
    "fmcli list-alarms",
    "traceroute o-ru",
    "man traceroute",
]


class TrainingPlayer(HttpUser):
    # Short think-time so 100 users produce real request volume. Lower it to push CPU harder.
    wait_time = between(0.05, 0.2)

    def on_start(self):
        """Log in as a fresh player and start a solo training game."""
        self.session_id = None
        self.player_id = None
        self.open_incidents = []

        username = "load_" + uuid.uuid4().hex[:12]
        with self.client.post(
            "/api/users/login", json={"username": username},
            name="POST /users/login", catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"login -> {resp.status_code}")
                return
            user_id = resp.json().get("id")

        with self.client.post(
            "/api/sessions/training", json={"userId": user_id, "durationSeconds": 1800},
            name="POST /sessions/training", catch_response=True,
        ) as resp:
            if resp.status_code not in (200, 201):
                resp.failure(f"training -> {resp.status_code}")
                return
            body = resp.json()
            self.session_id = body["session"]["id"]
            self.player_id = body["playerId"]

    @task(5)
    def poll_incidents(self):
        if not self.session_id:
            return
        with self.client.get(
            f"/api/sessions/{self.session_id}/incidents?playerId={self.player_id}",
            name="GET /incidents", catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                try:
                    self.open_incidents = [i["id"] for i in resp.json() if i.get("status") == "OPEN"]
                except ValueError:
                    pass

    @task(2)
    def poll_cells(self):
        if self.session_id:
            self.client.get(
                f"/api/sessions/{self.session_id}/cells?playerId={self.player_id}",
                name="GET /cells",
            )

    @task(2)
    def poll_scores(self):
        if self.session_id:
            self.client.get(f"/api/sessions/{self.session_id}/scores", name="GET /scores")

    @task(4)
    def investigate(self):
        """Heavier path: run a console command on an open incident (engine + persistence)."""
        if not self.session_id or not self.open_incidents:
            return
        incident_id = random.choice(self.open_incidents)
        self.client.post(
            f"/api/sessions/{self.session_id}/incidents/{incident_id}/console",
            json={"playerId": self.player_id, "command": random.choice(COMMANDS)},
            name="POST /console",
        )
