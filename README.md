# Slotify

Find the perfect meeting slot for everyone.

## Background

### Original Assignment

Build a calendar scheduling system that finds available meeting slots for multiple participants:
- Input: CSV file with calendar events (`Person name, Event subject, Event start time, Event end time`)
- Output: Hourly-aligned time slots where all participants are free
- Working hours: 07:00 to 19:00

### Extensions Added

| Feature | Description |
|---------|-------------|
| **Optional Participants** | Support for required + optional participants with availability ranking |
| **Blackout Periods** | Blocked time windows (e.g., lunch 12:00-13:00) from a separate CSV |
| **Buffer Time** | Configurable gap between meetings |
| **Web App** | Visual timeline UI in addition to the console app |

## Quick Start

### Build

```bash
mvn clean install
```

### Run the CLI App

#### Option 1: Docker

```bash
# Build the CLI image
docker build -f Dockerfile.cli -t slotify-app .

# Run with sample data
docker run -it slotify-app

# Run with your own CSV (pipe via stdin)
cat calendar.csv | docker run -i slotify-app -
```

#### Option 2: Maven

```bash
mvn -pl slotify-app exec:java -Dexec.args="slotify-app/src/main/resources/calendar.csv"
```

Options:
- `-b, --blackout <file>` - Blackout periods CSV file
- `--buffer <minutes>` - Buffer minutes between meetings

Example with blackout and buffer:
```bash
mvn -pl slotify-app exec:java -Dexec.args="slotify-app/src/main/resources/calendar.csv -b slotify-app/src/main/resources/blackout.csv --buffer 15"
```

### Run the Web App

#### Option 1: Docker Compose (recommended)

```bash
docker-compose up --build
```

Open http://localhost:8080

#### Option 2: Maven

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Run web app
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

## CSV Format

### Calendar File

```csv
Alice,Team standup,8:00,8:30
Alice,Project review,8:30,9:30
Jack,Team standup,8:00,8:50
Jack,Code review,9:00,9:40
```

Format: `Participant name, Event subject, Start time (H:mm), End time (H:mm)`

### Blackout File

```csv
12:00,13:00
```

Format: `Start time (H:mm), End time (H:mm)`

## Project Structure

```
slotify/
├── slotify-core/       # Domain models, business logic, repository interfaces
├── slotify-app/        # CLI application (picocli) - uses in-memory storage
├── slotify-web/        # Web UI (Javalin) - uses Redis storage
└── k8s/                # Kubernetes manifests
```

## Features

- Find available meeting slots for multiple participants
- Optional participants support
- Buffer time between meetings
- Blackout periods
- Web UI with timeline visualization

## Web UI

### How It Works

**Step 1: Load Availabilities**

Upload a CSV calendar file to load participant schedules. The timeline immediately visualizes each person's busy/free time:

```
        07  08  09  10  11  12  13  14  15  16  17  18  19
Alice   ░░░░████████░░░░░░░░░░░░████░░░░░░░░████░░░░░░░░
Jack    ░░░░████████████░░░░░░░░████░░░░░░░░████░░░░░░░░

░ Free (light green)  █ Busy (red)
```

**Step 2: Query Meeting Slots**

Select participants, choose meeting duration (30/60/90/120 min), and optionally add buffer time. Click "Find Available Slots" to query.

**Step 3: Visualize Results**

Available meeting slots are highlighted in green on the timeline:

```
        07  08  09  10  11  12  13  14  15  16  17  18  19
Alice   ▓▓▓▓████████▓▓▓▓▓▓▓▓▓▓▓▓████▓▓▓▓▓▓▓▓████▓▓▓▓▓▓▓▓
Jack    ▓▓▓▓████████████▓▓▓▓▓▓▓▓████▓▓▓▓▓▓▓▓████▓▓▓▓▓▓▓▓

▓ Available slot (green) - times where ALL selected participants are free
```

The slots are also listed as clickable chips: `07:00` `12:00` `15:00` `17:00`

See [DESIGN.md](DESIGN.md) for architecture details.
