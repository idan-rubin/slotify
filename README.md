# Slotify

Find the perfect meeting slot for everyone.

## Quick Start

### Run Locally with Docker Compose

```bash
docker-compose up --build
```

Open http://localhost:8080

### Run Locally with Maven

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Build and run
mvn clean install
mvn -pl slotify-web exec:java
```

Open http://localhost:8080

## Deploy to Kubernetes

### Prerequisites
- kubectl configured for your cluster
- Container registry access (e.g., Azure ACR)

### Build and Push Image

```bash
# Build for linux/amd64
docker buildx build --platform linux/amd64 -t <your-registry>/slotify-web:latest --push .
```

### Deploy

```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Deploy Redis and web app
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/slotify-web.yaml

# Check status
kubectl get pods -n slotify

# Get external IP
kubectl get svc slotify-web -n slotify
```

## Project Structure

```
slotify/
├── slotify-contract/   # DTOs and interfaces
├── slotify-core/       # Business logic
├── slotify-app/        # Console app
├── slotify-web/        # Web UI (Javalin)
└── k8s/                # Kubernetes manifests
```

## Features

- Find available meeting slots for multiple participants
- Optional participants support
- Buffer time between meetings
- Blackout periods
- Web UI for CSV upload

See [DESIGN.md](DESIGN.md) for architecture details.
