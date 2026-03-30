# jMeter Next Helm Chart

Kubernetes Helm chart for deploying jMeter Next in distributed mode with a controller and auto-discoverable worker nodes.

## Prerequisites

- Kubernetes 1.25+
- Helm 3.10+

## Install

```bash
helm install b3meter ./deploy/helm/b3meter
```

## With 5 workers

```bash
helm install b3meter ./deploy/helm/b3meter --set worker.replicaCount=5
```

## With ingress

```bash
helm install b3meter ./deploy/helm/b3meter \
  --set controller.ingress.enabled=true \
  --set controller.ingress.hosts[0].host=jmeter.example.com
```

## With autoscaling

```bash
helm install b3meter ./deploy/helm/b3meter \
  --set worker.autoscaling.enabled=true \
  --set worker.autoscaling.minReplicas=3 \
  --set worker.autoscaling.maxReplicas=20
```

## Architecture

```
Ingress -> Controller (StatefulSet, 1 replica)
               |
               +-- gRPC --> Worker-0 (Deployment)
               +-- gRPC --> Worker-1
               +-- gRPC --> Worker-N
```

- **Controller**: StatefulSet with persistent volume for H2 database. Exposes HTTP API (8080) and Web UI (3000).
- **Workers**: Deployment with N replicas behind a headless Service. Each worker exposes gRPC (9090) and health HTTP (8090).
- **Discovery**: Controller resolves worker pod IPs via DNS on the headless Service.
- **Autoscaling**: Optional HPA scales workers based on CPU utilization.

## Configuration

See `values.yaml` for all configurable parameters.

| Parameter | Description | Default |
|-----------|-------------|---------|
| `controller.replicaCount` | Controller replicas (should be 1) | `1` |
| `controller.image.repository` | Controller image | `b3meter/controller` |
| `controller.image.tag` | Controller image tag | `latest` |
| `controller.service.httpPort` | API port | `8080` |
| `controller.service.uiPort` | Web UI port | `3000` |
| `controller.ingress.enabled` | Enable ingress | `false` |
| `worker.replicaCount` | Number of worker pods | `3` |
| `worker.image.repository` | Worker image | `b3meter/worker` |
| `worker.image.tag` | Worker image tag | `latest` |
| `worker.grpcPort` | Worker gRPC port | `9090` |
| `worker.healthPort` | Worker health check port | `8090` |
| `worker.autoscaling.enabled` | Enable HPA | `false` |
| `worker.autoscaling.minReplicas` | HPA min replicas | `2` |
| `worker.autoscaling.maxReplicas` | HPA max replicas | `10` |
| `worker.autoscaling.targetCPUUtilizationPercentage` | HPA CPU target | `70` |
| `persistence.enabled` | Enable PVC for controller | `true` |
| `persistence.size` | PVC size | `1Gi` |

## Verify

```bash
helm test b3meter
```

## Uninstall

```bash
helm uninstall b3meter
```
