# Running on Kubernetes (local, minikube)

Structurally mirrors `docker-compose.yml`, with two deliberate differences: `payment-core-service`
and `auth-service` are `ClusterIP` (internal-only - docker-compose publishes every port for local
dev convenience, this is the more accurate topology), and secrets are split into three focused
`Secret` manifests instead of one overloaded one.

## Prerequisites

A local cluster - these instructions assume [minikube](https://minikube.sigs.k8s.io/) with the
Docker driver, and `kubectl` on your `PATH`.

## Deploy

```bash
minikube start --driver=docker

# Build all 4 images into minikube's own Docker daemon (not the host's) - imagePullPolicy:
# Never below means Kubernetes will only ever look for images already present on the node,
# it never pulls from a registry.
eval $(minikube docker-env)
docker build -t payment-core-service:latest ./payment-core-service
docker build -t auth-service:latest ./auth-service
docker build -t gateway-service:latest ./gateway-service
docker build -t frontend:latest ./frontend
eval $(minikube docker-env -u)   # switch the shell back to the host's Docker daemon

kubectl apply -f k8s/
kubectl get pods -n banking -w   # Ctrl+C once everything shows Running/Ready
```

`payment-core-service` needs a reachable MySQL for Liquibase/Hibernate at startup, so it will
likely restart once or twice while `mysql`'s pod comes up first - that's expected, not a
failure; Kubernetes' default restart policy handles it without any extra wiring.

## Access it

```bash
minikube service frontend -n banking --url
minikube service gateway-service -n banking --url
```

(`minikube service` resolves the actual reachable URL for a NodePort Service, since the
cluster's internal IP usually isn't directly routable from the host under the Docker driver.)

## Tear down

```bash
kubectl delete namespace banking   # deletes every namespaced resource above in one shot
minikube delete                    # or, to remove the whole cluster
```
