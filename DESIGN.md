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
| **Output** | Hourly-aligned slots where ALL participants are free for the meeting duration |

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

1. Multi-module Maven layout: contracts, core, app, web
2. Modern, clean code (Java 17+ features, no boilerplate)
3. Proper interfaces (program to interfaces, not implementations)
4. Redis for storage (not in-memory or traditional DB)
5. No useless comments (self-documenting code)
6. Pre-processing moved to input ingestion to simplify the scheduling API

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
│   5. Generate hourly-aligned slots                                      │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         REDIS REPOSITORY                                 │
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
│  Store Schedule objects in Redis                                │
└─────────────────────────────────────────────────────────────────┘

PHASE 2 - QUERY TIME (lightweight):
┌─────────────────────────────────────────────────────────────────┐
│  findAvailableSlots(["Alice", "Jack"], 60min)                   │
│       ↓                                                         │
│  Fetch pre-computed Schedules from Redis                        │
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
├── Dockerfile                      # Multi-stage build for web app
├── k8s/                            # Kubernetes manifests
│
├── slotify-contract/               # API CONTRACTS MODULE
│   └── src/main/java/io/slotify/contract/
│       ├── TimeSlot.java           # Immutable time range DTO
│       ├── CalendarEvent.java      # Event DTO
│       ├── AvailableSlot.java      # Result with optional participant info
│       ├── SchedulingOptions.java  # Buffer time configuration
│       ├── SchedulingService.java  # Main service interface
│       └── SchedulerException.java # Exception with error types
│
├── slotify-core/                   # BUSINESS LOGIC MODULE
│   └── src/main/java/io/slotify/core/
│       ├── Schedule.java           # Pre-computed busy slots per person
│       ├── ScheduleRepository.java # Repository interface
│       ├── RedisScheduleRepository.java
│       ├── CsvCalendarParser.java  # Parses calendar.csv + blackout.csv
│       └── DefaultSchedulingService.java
│
├── slotify-app/                    # CONSOLE APPLICATION MODULE
│   └── src/
│       ├── main/java/io/slotify/App.java
│       └── main/resources/
│           ├── calendar.csv        # Input data
│           └── blackout.csv        # Forbidden time slots
│
└── slotify-web/                    # WEB APPLICATION MODULE
    └── src/
        ├── main/java/io/slotify/web/WebApp.java
        └── main/resources/static/
            └── index.html          # Web UI with timeline visualization
```

**Module Responsibilities:**

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| `slotify-contract` | DTOs, interfaces, exceptions (API surface) | None |
| `slotify-core` | Business logic, algorithms, implementations | contract |
| `slotify-app` | Console entry point, configuration, resources | core |
| `slotify-web` | Web UI with Javalin, REST API, visualization | core |

**Why this structure?**
- External clients can depend only on `slotify-contract` without implementation details
- Clear separation of concerns
- Supports future packaging as a library

---

## 5. Design Decisions

### 5.1 Why Repository Pattern with Redis?

| Alternative | Pros | Cons | Decision |
|-------------|------|------|----------|
| In-memory Map | Simple, fast | Lost on restart, not scalable | No |
| Database (SQL) | ACID, queryable | Overkill for single-day, complex setup | No |
| **Redis** | Fast, persistent, simple API, Docker-friendly | Requires Docker | **Yes** |

Redis provides the right balance: persistent enough for the demo, simple to set up with Docker, and demonstrates real-world patterns.

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

### 5.4 Why Builder Pattern for SchedulingOptions?

```java
// Clean, readable API:
SchedulingOptions.withBuffer(Duration.ofMinutes(15))

// vs constructor with many parameters:
new SchedulingOptions(Duration.ofMinutes(15), null, null, false)
```

Builder pattern provides a fluent API and makes defaults explicit.

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

    // Basic with options (buffer time)
    List<TimeSlot> findAvailableSlots(
        List<String> participants,
        Duration meetingDuration,
        SchedulingOptions options
    );

    // Advanced: required + optional participants
    List<AvailableSlot> findAvailableSlots(
        List<String> requiredParticipants,
        List<String> optionalParticipants,
        Duration meetingDuration
    );

    // Advanced with options
    List<AvailableSlot> findAvailableSlots(
        List<String> requiredParticipants,
        List<String> optionalParticipants,
        Duration meetingDuration,
        SchedulingOptions options
    );
}
```

### 6.2 DTOs

```java
// Immutable time range (single day, LocalTime only)
public final class TimeSlot implements Comparable<TimeSlot> {
    private final LocalTime start;
    private final LocalTime end;
}

// Result with optional participant availability
public final class AvailableSlot {
    private final TimeSlot timeSlot;
    private final List<String> availableOptionalParticipants;
    private final List<String> unavailableOptionalParticipants;
}

// Configurable options
public final class SchedulingOptions {
    private final Duration bufferBetweenMeetings;

    public static SchedulingOptions defaults();
    public static SchedulingOptions withBuffer(Duration buffer);
}
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
| Contract classes | 6 | TimeSlot, CalendarEvent, AvailableSlot, SchedulingOptions, SchedulingService, SchedulerException |
| Core classes | 5 | Schedule, ScheduleRepository, RedisScheduleRepository, CsvCalendarParser, DefaultSchedulingService |
| App classes | 1 | App |
| Web classes | 1 | WebApp |
| Web resources | 1 | index.html (with timeline visualization) |
| Test classes | 5 | ScheduleTest, CsvCalendarParserTest, DefaultSchedulingServiceTest, TimeSlotTest, SchedulingIntegrationTest |
| Config files | 7 | 5 POMs + docker-compose.yml + Dockerfile |
| K8s manifests | 3 | namespace.yaml, redis.yaml, slotify-web.yaml |
| Resources | 3 | calendar.csv, blackout.csv, test-calendar.csv |
| **Total** | **32** | |

---

## 10. Tech Stack

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

## 11. Web UI

### Timeline Visualization

The web UI includes a visual timeline showing each participant's availability for the day (07:00-19:00):

```
        07  08  09  10  11  12  13  14  15  16  17  18  19
Alice   ░░░░████████░░░░░░░░░░░░████░░░░░░░░████░░░░░░░░
Jack    ░░░░████████████░░░░░░░░████░░░░░░░░████░░░░░░░░
Bob     ░░░░██████████████████████████████████████░░░░░░

Legend: ░ Free (light green)  █ Busy (red)  ▓ Available slot (green)
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
| `/api/upload` | POST | Upload CSV, returns participants + busy slots |
| `/api/availability` | POST | Find available slots for selected participants |
