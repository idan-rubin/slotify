package io.slotify.web;

import io.slotify.core.model.Constants;
import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.AvailableSlot;
import io.slotify.core.model.TimeSlot;
import io.slotify.core.parser.CsvCalendarParser;
import io.slotify.core.repository.InMemoryScheduleRepository;
import io.slotify.core.repository.RedisScheduleRepository;
import io.slotify.core.repository.ScheduleRepository;
import io.slotify.core.service.DefaultSchedulingService;
import io.slotify.core.service.SchedulingService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class WebApp {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_DURATION_MINUTES = 480; // 8 hours
    private static final int MAX_PARTICIPANTS = 100;
    private static final int MAX_BUFFER_MINUTES = 60;
    private static final int MAX_BLACKOUTS = 10;
    private static final String INVALID_CHARS = ":*[]{}\\\"'";
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final ScheduleRepository repository;
    private final CsvCalendarParser parser = new CsvCalendarParser();
    private final SchedulingService service;
    private final JedisPool jedisPool;
    private final ReadWriteLock dataLock = new ReentrantReadWriteLock();

    public WebApp() {
        var config = Config.get();
        var redisHost = config.redisHost();
        if (redisHost != null) {
            this.jedisPool = new JedisPool(redisHost, config.redisPort());
            this.repository = new RedisScheduleRepository(jedisPool);
        } else {
            this.jedisPool = null;
            this.repository = new InMemoryScheduleRepository();
        }

        var buffer = config.bufferBetweenMeetings();
        this.service = new DefaultSchedulingService(repository, List.of(), buffer);
    }

    public void shutdown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    public static void main(String[] args) {
        var app = new WebApp();
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        Javalin.create(config -> {
                    config.staticFiles.add("/static");
                })
                .exception(ValidationException.class, (e, ctx) ->
                        ctx.status(400).json(Map.of("error", e.getMessage())))
                .exception(SchedulerException.class, (e, ctx) ->
                        ctx.status(400).json(Map.of("error", e.getMessage())))
                .exception(Exception.class, (e, ctx) ->
                        ctx.status(500).json(Map.of("error", "Internal server error")))
                .post("/api/upload", app::uploadWithSSE)
                .post("/api/availability", app::availability)
                .post("/api/meeting-request", app::meetingRequest)
                .start(8080);
    }

    private void uploadWithSSE(Context ctx) {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) {
            ctx.status(400).json(Map.of("error", "No file uploaded"));
            return;
        }

        var filename = file.filename().toLowerCase();
        if (!filename.endsWith(".csv")) {
            ctx.status(400).json(Map.of("error", "Only CSV files are allowed"));
            return;
        }

        if (file.size() > MAX_FILE_SIZE) {
            ctx.status(400).json(Map.of("error", "File too large (max 5MB)"));
            return;
        }

        Path temp;
        try {
            temp = Files.createTempFile("calendar", ".csv");
            try (InputStream in = file.content();
                 OutputStream out = Files.newOutputStream(temp)) {
                in.transferTo(out);
            }
        } catch (IOException e) {
            ctx.status(500).json(Map.of("error", "Failed to save file"));
            return;
        }

        ctx.contentType("text/event-stream");
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");
        ctx.header("X-Accel-Buffering", "no");

        try {
            sendSSE(ctx, "progress", "{\"message\":\"Parsing CSV file...\"}");

            var schedules = parser.parseAndBuildSchedules(temp);

            sendSSE(ctx, "progress", "{\"message\":\"Found " + schedules.size() + " participants\"}");

            sendSSE(ctx, "progress", "{\"message\":\"Saving schedules...\"}");

            dataLock.writeLock().lock();
            try {
                repository.clear();
                schedules.values().forEach(repository::save);
            } finally {
                dataLock.writeLock().unlock();
            }

            sendSSE(ctx, "progress", "{\"message\":\"Building response...\"}");

            var busySlotsMap = schedules.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().busySlots().stream()
                                    .map(BusySlotResponse::from)
                                    .toList()
                    ));
            var participants = schedules.keySet().stream().sorted().toList();

            var result = new UploadResponse(participants, busySlotsMap);
            sendSSE(ctx, "done", JSON_MAPPER.writeValueAsString(result));

        } catch (Exception e) {
            try {
                sendSSE(ctx, "error", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (IOException ignored) {}
        } finally {
            deleteTempFile(temp);
        }
    }

    private void sendSSE(Context ctx, String event, String data) throws IOException {
        ctx.res().getWriter().write("event: " + event + "\n");
        ctx.res().getWriter().write("data: " + data + "\n\n");
        ctx.res().getWriter().flush();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void availability(Context ctx) {
        var body = ctx.bodyAsClass(AvailabilityRequest.class);

        validateParticipantList(body.participants(), "participants", true);
        validateDuration(body.durationMinutes());

        var duration = Duration.ofMinutes(body.durationMinutes());

        var slots = withReadLock(() -> service.findAvailableSlots(body.participants(), duration));
        var startTimes = slots.stream().map(TimeSlot::start).map(Object::toString).toList();
        ctx.json(Map.of("slots", startTimes));
    }

    private void meetingRequest(Context ctx) {
        var body = ctx.bodyAsClass(MeetingRequest.class);
        var optional = body.optional() != null ? body.optional() : List.<String>of();

        validateParticipantList(body.required(), "required participants", true);
        validateParticipantList(optional, "optional participants", false);
        validateDuration(body.durationMinutes());
        validateTotalParticipants(body.required().size() + optional.size());
        validateNoOverlap(body.required(), optional);

        var duration = Duration.ofMinutes(body.durationMinutes());
        validateBuffer(body.bufferMinutes());
        validateBlackouts(body.blackouts());
        var buffer = body.bufferMinutes() > 0 ? Duration.ofMinutes(body.bufferMinutes()) : null;
        var blackouts = parseBlackouts(body.blackouts());

        var requestService = new DefaultSchedulingService(repository, blackouts, buffer);
        var slots = withReadLock(() -> requestService.findAvailableSlots(body.required(), optional, duration));
        var result = slots.stream().map(SlotResponse::from).toList();
        ctx.json(Map.of("slots", result));
    }

    private List<TimeSlot> parseBlackouts(List<BlackoutRequest> blackouts) {
        if (blackouts == null || blackouts.isEmpty()) {
            return List.of();
        }
        return blackouts.stream()
                .map(b -> new TimeSlot(LocalTime.parse(b.start()), LocalTime.parse(b.end())))
                .toList();
    }

    private <T> T withReadLock(Supplier<T> action) {
        dataLock.readLock().lock();
        try {
            return action.get();
        } finally {
            dataLock.readLock().unlock();
        }
    }

    private void validateParticipantList(List<String> participants, String fieldName, boolean required) {
        if (participants == null || participants.isEmpty()) {
            if (required) {
                throw new ValidationException("At least one " + fieldName.replace("participants", "participant") + " is required");
            }
            return;
        }
        if (participants.size() > MAX_PARTICIPANTS) {
            throw new ValidationException("Too many " + fieldName + " (max " + MAX_PARTICIPANTS + ")");
        }
        if (participants.size() != Set.copyOf(participants).size()) {
            throw new ValidationException("Duplicate " + fieldName + " not allowed");
        }
        participants.forEach(this::validateParticipantName);
    }

    private void validateParticipantName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Participant name cannot be empty");
        }
        if (name.length() > Constants.MAX_NAME_LENGTH) {
            throw new ValidationException("Participant name too long");
        }
        INVALID_CHARS.chars()
                .filter(c -> name.indexOf(c) >= 0)
                .findFirst()
                .ifPresent(c -> {
                    throw new ValidationException("Participant name contains invalid character: " + (char) c);
                });
    }

    private void validateDuration(int durationMinutes) {
        if (durationMinutes <= 0 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new ValidationException("Duration must be between 1 and " + MAX_DURATION_MINUTES + " minutes");
        }
    }

    private void validateBuffer(int bufferMinutes) {
        if (bufferMinutes < 0 || bufferMinutes > MAX_BUFFER_MINUTES) {
            throw new ValidationException("Buffer must be between 0 and " + MAX_BUFFER_MINUTES + " minutes");
        }
    }

    private void validateBlackouts(List<BlackoutRequest> blackouts) {
        if (blackouts == null || blackouts.isEmpty()) {
            return;
        }
        if (blackouts.size() > MAX_BLACKOUTS) {
            throw new ValidationException("Too many blocked times (max " + MAX_BLACKOUTS + ")");
        }
        for (var b : blackouts) {
            if (b.start() == null || b.end() == null) {
                throw new ValidationException("Blocked time must have start and end");
            }
            if (b.start().compareTo(b.end()) >= 0) {
                throw new ValidationException("Blocked time end must be after start");
            }
        }
    }

    private void validateTotalParticipants(int total) {
        if (total > MAX_PARTICIPANTS) {
            throw new ValidationException("Too many participants (max " + MAX_PARTICIPANTS + ")");
        }
    }

    private void validateNoOverlap(List<String> required, List<String> optional) {
        if (!Collections.disjoint(required, optional)) {
            throw new ValidationException("Participant cannot be both required and optional");
        }
    }

    private void deleteTempFile(Path temp) {
        if (temp != null) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    record AvailabilityRequest(List<String> participants, int durationMinutes) {}

    record MeetingRequest(List<String> required, List<String> optional, int durationMinutes, int bufferMinutes, List<BlackoutRequest> blackouts) {}

    record BlackoutRequest(String start, String end) {}

    record SlotResponse(String start, String end, List<String> availableOptional, List<String> unavailableOptional) {
        static SlotResponse from(AvailableSlot slot) {
            return new SlotResponse(
                    slot.timeSlot().start().toString(),
                    slot.timeSlot().end().toString(),
                    slot.availableOptionalParticipants(),
                    slot.unavailableOptionalParticipants()
            );
        }
    }

    record BusySlotResponse(String start, String end) {
        static BusySlotResponse from(TimeSlot slot) {
            return new BusySlotResponse(slot.start().toString(), slot.end().toString());
        }
    }

    record UploadResponse(List<String> participants, Map<String, List<BusySlotResponse>> busySlots) {}

    private static class ValidationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ValidationException(String message) {
            super(message);
        }
    }
}
