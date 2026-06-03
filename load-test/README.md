# Load test — 100 players, autoscaling demo

Drives the backend with ~100 virtual "players" (each running a solo training game) so the
**HorizontalPodAutoscaler** scales the backend Deployment up (2 → up to 5 pods) under load and
back down once it stops. Built with [Locust](https://locust.io) (`pip install locust`).

## Why it scales
The HPA targets **70% of the backend's CPU *request* (250m)** — i.e. ~**175m per pod** — so it
doesn't take much sustained load to cross the threshold. The backend is **stateless** (all state
in MySQL), so Kubernetes can add/remove pods transparently.

## Run it

```bash
# 1. point Locust at the in-cluster backend
kubectl -n oran port-forward svc/backend-service 8080:8080

# 2. (another terminal) watch the autoscaler + pods live.
#    NOTE: `kubectl -w` only watches ONE resource type, so use a refresh loop for both:
while true; do clear; date; \
  kubectl -n oran get hpa backend-hpa; echo; \
  kubectl -n oran get pods -l app=backend; sleep 2; done
#    (or two terminals: `kubectl -n oran get hpa -w` and `kubectl -n oran get pods -l app=backend -w`)

# 3. (another terminal) start Locust — web UI on http://localhost:8089
locust -f load-test/locustfile.py --host http://localhost:8080
#    then set "Number of users" = 100, "Ramp up" = 10, and Start.

# ...or headless:
locust -f load-test/locustfile.py --host http://localhost:8080 --headless -u 100 -r 10 -t 5m
```

## What to capture (for the slide)
- **Scale-up:** in the watch-loop terminal, the HPA CPU% climbs past 70% and
  `REPLICAS` rises 2 → 3 → 4 → 5; new `backend-*` pods appear and go `Running`.
- **Scale-down:** **stop Locust** — CPU falls and replicas drop back to 2 within ~1 minute
  (we set a 30s scale-down stabilization window in the HPA `behavior`, vs the 5-min default).
- Locust's own dashboard (users, requests/sec) makes a nice side-by-side with the pod view.

## Notes / honest findings
- This populates the **cluster** MySQL with test users/sessions (not the local Docker DB).
  Clean up afterwards with `kubectl -n oran delete pod db-0` is **not** enough (PVC persists);
  to reset, redeploy or truncate as in the main DB-purge steps.
- The first things to strain at scale are **not** the backend — they're the **single simulator**
  (one replica, by design) and the **DB**, exactly as the architecture slide predicts. The
  documented next steps are server-sent events instead of polling, and sharding the simulator.
- Tune load with `wait_time` in `locustfile.py` (lower = more requests/sec per user).
