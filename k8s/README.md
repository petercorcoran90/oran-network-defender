# Kubernetes deployment

Manifests for the full stack: MySQL (StatefulSet), backend (Deployment ×2 + Service + HPA),
frontend (Deployment + LoadBalancer Service), and the Python simulator (Deployment).

## Prerequisites
- A cluster + `kubectl` (tested on Docker Desktop Kubernetes, which shares the local Docker
  image cache so no registry is needed).
- The three images built locally with the names the manifests expect:

```bash
docker build -t oran-network-defender-backend:latest   ./backend
docker build -t oran-network-defender-frontend:latest  ./frontend
docker build -t oran-network-defender-simulator:latest ./simulator
```

(The Deployments use `imagePullPolicy: IfNotPresent`, so these local images are used as-is.)

## Deploy

```bash
kubectl create namespace oran

# Secret — never committed. Generate it from your .env plus a fresh ingest token:
set -a; . ./.env; set +a
kubectl -n oran create secret generic oran-defender-secrets \
  --from-literal=DB_USER="$DB_USER" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=DB_ROOT_PASSWORD="$DB_ROOT_PASSWORD" \
  --from-literal=SIM_INGEST_TOKEN="$(openssl rand -hex 16)"

kubectl -n oran apply -f k8s/          # skips secret.yaml.example (not a .yaml)
kubectl -n oran rollout status deploy/backend
```

## Access

```bash
kubectl -n oran port-forward svc/frontend-service 8080:80   # then open http://localhost:8080
```

The frontend Service is also a `LoadBalancer` (NodePort fallback on Docker Desktop).

## Autoscaling

`backend-hpa` scales the backend on CPU (2→5 replicas, target 70%). It needs metrics-server
in the cluster (bundled with Docker Desktop); check it with `kubectl -n oran get hpa`.

## Notes
- All pods declare resource requests/limits (required for the HPA).
- On a cold start the backend may restart once or twice while MySQL initialises, then settles —
  Kubernetes restarts it until the DB is ready. (A `wait-for-db` initContainer would remove that
  churn; left out to avoid an extra image pull.)
- Teardown: `kubectl delete namespace oran`.
