# Slotify - Design Document

## 1. Overview

A calendar scheduling system that finds available meeting slots for multiple participants. Built as a Gong.io engineering take-home assignment demonstrating OOP design, SOLID principles, and enterprise-grade project organization.

---

## 2. Requirements

### 2.1 Assignment Requirements (Given)

| Requirement | Description |
|-------------|-------------|
| **Core Method** | `List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration)` |
| **Input Format** | CSV file: `Person name, Event subject, Event start time, Event end time` |
| **Working Hours** | 07:00 to 19:00 (24-hour format) |
| **Scope** | Single day only |
| **Output** | Aligned slots where ALL participants are free (hourly for 60+ min, half-hourly for 30 min) |

**Example Output** (Alice + Jack, 60 min meeting):
```
07:00, 10:00, 11:00, 12:00, 14:00, 15:00, 17:00, 18:00
```

### 2.2 Additional Requirements (Extended)

| Requirement | Description |
|-------------|-------------|
| **Optional Participants** | Overloaded method with required + optional participants. Returns slots ranked by optional participant availability |
| **Buffer Time** | Configurable X minutes gap between meetings |
| **Blackout Periods** | Forbidden time slots loaded from `blackout.csv` (e.g., lunch 12:00-13:00) |
| **Extensibility** | Design should support future multi-day scheduling without major refactoring |

---

## 3. AI Assistance

### Tools Used
- **Claude Code** (Anthropic's CLI tool)

### My Guidelines

1. Multi-module Maven layout: core, app, web
2. Package structure: `io.slotify.core.*` for core, `io.slotify.*` for apps
3. Modern, clean code (Java 17+ features, no boilerplate)
4. Proper interfaces (program to interfaces, not implementations)
5. Redis for web app storage (in-memory for CLI)
6. No useless comments (self-documenting code)
7. Pre-processing moved to input ingestion to simplify the scheduling API
8. Modular web frontend: separate CSS (`style.css`) and JS (`app.js`) files

### Where AI Assisted

| Area | AI Contribution |
|------|-----------------|
| **Implementation** | Wrote code following my guidelines above |
| **Code Reviews** | Found unused methods, imports, inconsistent exceptions |
| **Algorithm Verification** | Validated interval merging, gap finding logic |
| **Web Module** | Generated minimal Javalin implementation |
| **Naming** | Suggested "Slotify" as the project name |

---

## 4. Architecture

### 4.1 High-Level Design

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              CLIENT                                      │
│                                                                          │
│   findAvailableSlots(["Alice", "Jack"], Duration.ofMinutes(60))         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        SCHEDULING SERVICE                                │
│                                                                          │
│   1. Fetch pre-computed schedules from repository                       │
│   2. Combine busy slots + blackout periods                              │
│   3. Apply buffer time (if configured)                                  │
│   4. Find gaps within working hours                                     │
│   5. Generate aligned slots (hourly or half-hourly based on duration)  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        SCHEDULE REPOSITORY                               │
│                  (Redis for web, In-memory for CLI)                      │
│                                                                          │
│   Pre-computed Schedule objects with merged busy slots per participant  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Two-Phase Data Flow

**Why two phases?** Separating load-time preprocessing from query-time execution keeps `findAvailableSlots` fast and simple.

```
PHASE 1 - LOAD TIME (once, during initialization):
┌─────────────────────────────────────────────────────────────────┐
│  calendar.csv                                                   │
│       ↓                                                         │
│  Parse events → Group by participant                            │
│       ↓                                                         │
│  For each participant: merge overlapping busy slots             │
│       ↓                                                         │
│  Store Schedule objects in repository                           │
└─────────────────────────────────────────────────────────────────┘

PHASE 2 - QUERY TIME (lightweight):
┌─────────────────────────────────────────────────────────────────┐
│  findAvailableSlots(["Alice", "Jack"], 60min)                   │
│       ↓                                                         │
│  Fetch pre-computed Schedules from repository                   │
│       ↓                                                         │
│  Combine busy slots + blackouts + apply buffer                  │
│       ↓                                                         │
│  Find gaps → Return available slots                             │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Maven Multi-Module Structure

```
slotify/
├── pom.xml                         # Parent POM (aggregator)
├── docker-compose.yml              # Redis + web containers
├── Dockerfile                      # Multi-stage build (web + cli targets)
├── k8s/                            # Kubernetes manifests
│
├── slotify-core/                   # DOMAIN & BUSINESS LOGIC MODULE
│   └── src/main/java/io/slotify/core/
│       ├── exception/
│       │   └── SchedulerException.java
│       ├── model/
│       │   ├── TimeSlot.java       # Immutable time range
│       │   ├── CalendarEvent.java  # Event DTO
│       │   ├── Schedule.java       # Pre-computed busy slots per person
│       │   ├── AvailableSlot.java  # Result with optional participant info
│       │   └── Constants.java      # Shared constants
│       ├── parser/
│       │   ├── CalendarParser.java # Parser interface
│       │   └── CsvCalendarParser.java
│       ├── repository/
│       │   ├── ScheduleRepository.java
│       │   ├── InMemoryScheduleRepository.java
│       │   └── RedisScheduleRepository.java
│       └── service/
│           ├── SchedulingService.java
│           └── DefaultSchedulingService.java
│
├── slotify-app/                    # CLI APPLICATION MODULE
│   └── src/main/java/io/slotify/App.java
│
└── slotify-web/                    # WEB APPLICATION MODULE
    └── src/main/java/io/slotify/web/
        ├── WebApp.java             # Javalin REST API
        └── Config.java             # Configuration loader
```

**Module Responsibilities:**

| Module | Purpose | Storage | Dependencies |
|--------|---------|---------|--------------|
| `slotify-core` | Domain models, business logic, interfaces, implementations | - | None |
| `slotify-app` | CLI entry point with picocli | In-memory | core |
| `slotify-web` | Web UI with Javalin, REST API | Redis | core |

**Why this structure?**
- Clear separation of concerns
- Core module is self-contained with all domain logic
- App modules are thin wrappers around core

---

## 5. Design Decisions

### 5.1 Why Repository Pattern with Two Implementations?

| Implementation | Used By | Pros | Cons |
|----------------|---------|------|------|
| **InMemoryScheduleRepository** | CLI app | Simple, no dependencies | Lost on restart |
| **RedisScheduleRepository** | Web app | Persistent, scalable, Docker-friendly | Requires Redis |

The repository interface allows swapping storage implementations. The CLI app uses in-memory for simplicity (no external dependencies). The web app uses Redis for persistence across restarts and to demonstrate real-world patterns.

### 5.2 Why Pre-compute Busy Slots at Load Time?

**Goal:** Keep `findAvailableSlots` as simple as possible.

```java
// BEFORE (complex query-time):
public List<TimeSlot> findAvailableSlots(...) {
    // Fetch raw events
    // Group by participant
    // Merge overlapping slots  <-- Complex logic here
    // Find gaps
    // Return slots
}

// AFTER (simple query-time):
public List<TimeSlot> findAvailableSlots(...) {
    // Fetch pre-merged schedules  <-- Already done at load time
    // Combine + find gaps
    // Return slots
}
```

**Benefits:**
- Query method is simple and fast
- Merging logic tested once in `Schedule` class
- Scales better with many queries

### 5.3 Why Blackouts in a File vs. API Parameter?

| Approach | Pros | Cons |
|----------|------|------|
| API parameter | Flexible per-call | Clutters API, usually static |
| **Config file** | Simple, declarative, easy to edit | Less flexible per-call |

Blackouts (like lunch) are typically organization-wide policies, not per-query decisions. A config file is simpler and matches real-world usage.

### 5.4 Why External Configuration via Config Class?

```properties
# config.properties (example values)
redis.host=localhost
redis.port=6379
buffer.minutes=15
```

```java
// Config singleton loads from properties file
var config = Config.get();
var buffer = config.bufferBetweenMeetings();
var service = new DefaultSchedulingService(repository, blackouts, buffer);
```

Configuration is externalized to a properties file, making it easy to change without code modifications. The singleton `Config` class provides type-safe access to configuration values.

### 5.5 Why Single Exception with Error Types?

```java
// BEFORE (many exception classes):
throw new InvalidTimeRangeException(...);
throw new ParticipantNotFoundException(...);
throw new ParseException(...);

// AFTER (single exception):
throw new SchedulerException(ErrorType.INVALID_TIME_RANGE, ...);
throw new SchedulerException(ErrorType.PARTICIPANT_NOT_FOUND, ...);
```

**Benefits:**
- Fewer classes to maintain
- Single catch block for callers
- Error type enum is exhaustive and IDE-friendly

---

## 6. API Design

### 6.1 Service Interface

```java
public interface SchedulingService {

    // Basic: all participants required
    List<TimeSlot> findAvailableSlots(
        List<String> participants,
        Duration meetingDuration
    );

    // Advanced: required + optional participants
    List<AvailableSlot> findAvailableSlots(
        List<String> requiredParticipants,
        List<String> optionalParticipants,
        Duration meetingDuration
    );
}
```

Buffer time and blackout periods are configured at service construction time via `DefaultSchedulingService` constructor.

### 6.2 Domain Models (Records)

```java
// Immutable time range (single day, LocalTime only)
public record TimeSlot(LocalTime start, LocalTime end) {
    public boolean overlaps(TimeSlot other);
    public TimeSlot expandBy(Duration buffer);
    public static List<TimeSlot> mergeOverlapping(List<TimeSlot> slots);
    public static LocalTime max(LocalTime first, LocalTime second);
    public static LocalTime min(LocalTime first, LocalTime second);
}

// Result with optional participant availability
public record AvailableSlot(
    TimeSlot timeSlot,
    List<String> availableOptionalParticipants,
    List<String> unavailableOptionalParticipants
) {}

// Pre-computed busy slots per participant
public record Schedule(String participantName, List<TimeSlot> busySlots) {
    public static Schedule fromEvents(String participantName, List<CalendarEvent> events);
    public boolean isBusyDuring(TimeSlot timeSlot);
}

// Calendar event from CSV
public record CalendarEvent(String participantName, String subject, TimeSlot timeSlot) {}
```

---

## 7. Algorithm

### 7.1 Interval Merging (Load Time)

```
Input:  [08:00-09:00, 08:30-10:00, 14:00-15:00]
Output: [08:00-10:00, 14:00-15:00]

Algorithm:
1. Sort intervals by start time
2. For each interval:
   - If overlaps with previous → extend previous
   - Else → add as new interval

Time Complexity: O(n log n) for sorting
```

### 7.2 Gap Finding (Query Time)

```
Working Hours: [07:00 ─────────────────────────────── 19:00]
Busy Slots:         [08:00-09:40]  [13:00-14:00]  [16:00-17:00]
                    ────────────   ────────────   ────────────
Gaps:         [07:00-08:00]    [09:40-13:00]  [14:00-16:00]  [17:00-19:00]

Hourly Slots (60 min meeting):
              [07:00]  [10:00, 11:00, 12:00]  [14:00, 15:00]  [17:00, 18:00]

Half-Hourly Slots (30 min meeting):
              [07:00, 07:30]  [10:00, 10:30, 11:00, 11:30, 12:00, 12:30]  ...
```

### 7.3 Buffer Time Application

When buffer is 15 minutes:
```
Original busy: [09:00-10:00]
With buffer:   [08:45-10:15]  (expanded by buffer on both sides)
```

---

## 8. Extensibility

The design supports these future extensions without breaking changes:

| Extension | How to Implement |
|-----------|------------------|
| **Multi-day scheduling** | Add `LocalDate` to events, introduce `DateTimeSlot` |
| **Per-person working hours** | Add `WorkingHoursPolicy` interface |
| **Different input formats** | Implement `JsonCalendarParser`, `ApiCalendarParser` |
| **Different storage** | Implement `DatabaseScheduleRepository` |
| **Priority scheduling** | Add priority field to `CalendarEvent` |
| **Recurring events** | Add `RecurrenceRule` to `CalendarEvent` |

---

## 9. File Summary

| Category | Count | Files |
|----------|-------|-------|
| Core model classes | 5 | TimeSlot, CalendarEvent, Schedule, AvailableSlot, Constants |
| Core service classes | 2 | SchedulingService (interface), DefaultSchedulingService |
| Core repository classes | 3 | ScheduleRepository (interface), InMemoryScheduleRepository, RedisScheduleRepository |
| Core parser classes | 2 | CalendarParser (interface), CsvCalendarParser |
| Core exception | 1 | SchedulerException |
| App classes | 1 | App |
| Web classes | 2 | WebApp, Config |
| Test classes | 5 | TimeSlotTest, ScheduleTest, DefaultSchedulingServiceTest, CsvCalendarParserTest, SchedulingIntegrationTest |
| Config files | 7 | 4 POMs + docker-compose.yml + Dockerfile + config.properties |
| K8s manifests | 3 | namespace.yaml, redis.yaml, slotify-web.yaml |
| **Total** | **31** | |

---

## 10. Deployment Options

The project supports multiple deployment modes:

| Mode | Command | Storage | Use Case |
|------|---------|---------|----------|
| **CLI (Docker)** | `docker run -it slotify-cli` | In-memory | Quick testing, local use |
| **Web (Docker Compose)** | `docker-compose up` | Redis | Local development |
| **Web (Kubernetes)** | `kubectl apply -f k8s/` | Redis | Production deployment |

### CLI App (In-Memory)
```bash
# Build and run CLI
docker build --target cli -t slotify-cli .
docker run -it slotify-cli
```
The CLI app uses in-memory storage - no external dependencies required.

### Web App (Docker Compose)
```bash
docker-compose up --build
# Access at http://localhost:8080
```
Docker Compose orchestrates both the web app and Redis container.

### Web App (Kubernetes)
```bash
# Deploy to cluster
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/slotify-web.yaml
```
Kubernetes deployment includes Redis, health probes, and LoadBalancer service.

**Live Demo:** http://20.161.224.152 (Azure AKS)

---

## 11. Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 21 (for `getFirst()` on List) |
| Build | Maven | 3.x |
| Web Framework | Javalin | 6.x |
| Storage | Redis | 7 (Alpine) |
| Redis Client | Jedis | 5.1.0 |
| JSON | Jackson | 2.16.0 |
| Testing | JUnit 5 | 5.10.0 |
| Assertions | AssertJ | 3.24.2 |
| Container | Docker Compose | 3.8 |
| Orchestration | Kubernetes | AKS |

---

## 12. Web UI

### Timeline Visualization

The web UI includes a visual timeline showing each participant's availability for the day (07:00-19:00):

```
        07  08  09  10  11  12  13  14  15  16  17  18  19
Alice   ░░░░████████░░░░░░░░░░░░████░░░░░░░░████░░░░░░░░
Jack    ░░░░████████████░░░░░░░░████░░░░░░░░████░░░░░░░░
Bob     ░░░░██████████████████████████████████████░░░░░░

░ Free    █ Busy
```

### Features

- Upload CSV calendar files
- Select participants for meeting
- Choose meeting duration (30/60/90/120 min)
- Configure buffer time between meetings
- Visual timeline with busy/free blocks
- Available slots highlighted after search

### REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/upload` | POST | Upload CSV with SSE progress events |
| `/api/availability` | POST | Find available slots for selected participants |
| `/api/meeting-request` | POST | Find slots with required + optional participants |

### API Validation

The web API includes comprehensive input validation to prevent abuse and ensure data integrity:

| Validation | Limit | Description |
|------------|-------|-------------|
| File size | 5 MB | Maximum upload file size |
| File type | `.csv` | Only CSV files allowed |
| Lines per file | 10,000 | Maximum lines in CSV |
| Line length | 2,000 chars | Maximum characters per line |
| Participant name | 100 chars | Maximum name length |
| Subject length | 500 chars | Maximum event subject length |
| Duration | 30-120 min | Meeting duration (30/60/90/120 min) |
| Buffer | 0 or 5-15 min | Buffer between meetings |
| Required participants | 2+ | Minimum required participants |
| Blackouts | 10 max | Maximum blocked time periods |
| Participants | 100 max | Maximum participants per request |

Additional validations:
- No duplicate participants in request
- Participant cannot be both required and optional
- Invalid characters blocked in names (`:*[]{}\"'`)
- Blackout end time must be after start time

### Server-Sent Events (SSE)

File upload uses SSE for real-time progress updates:

```
POST /api/upload
Content-Type: multipart/form-data

Response (text/event-stream):
event: progress
data: {"message":"Parsing CSV file..."}

event: progress
data: {"message":"Found 3 participants"}

event: progress
data: {"message":"Saving schedules..."}

event: done
data: {"participants":["Alice","Bob"],"busySlots":{...}}
```
