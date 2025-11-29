package io.slotify.web;

import io.slotify.contract.SchedulingOptions;
import io.slotify.contract.SchedulingService;
import io.slotify.contract.TimeSlot;
import io.slotify.core.CsvCalendarParser;
import io.slotify.core.DefaultSchedulingService;
import io.slotify.core.RedisScheduleRepository;
import io.slotify.core.Schedule;
import io.slotify.core.ScheduleRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class WebApp {

    private final ScheduleRepository repository;
    private final CsvCalendarParser parser = new CsvCalendarParser();
    private SchedulingService service;

    public WebApp() {
        var redisHost = System.getenv("REDIS_HOST");
        if (redisHost != null) {
            var jedisPool = new JedisPool(redisHost, 6379);
            this.repository = new RedisScheduleRepository(jedisPool);
        } else {
            this.repository = new InMemoryRepository();
        }
        this.service = new DefaultSchedulingService(repository);
    }

    public static void main(String[] args) {
        var app = new WebApp();
        Javalin.create(config -> config.staticFiles.add("/static"))
                .post("/api/upload", app::upload)
                .post("/api/availability", app::availability)
                .start(8080);
    }

    private void upload(Context ctx) throws Exception {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) {
            ctx.status(400).json(Map.of("error", "No file uploaded"));
            return;
        }

        Path temp = Files.createTempFile("calendar", ".csv");
        try {
            try (var reader = new BufferedReader(new InputStreamReader(file.content()))) {
                Files.writeString(temp, reader.lines().collect(Collectors.joining("\n")));
            }

            repository.clear();
            var schedules = parser.parseAndBuildSchedules(temp);
            schedules.values().forEach(repository::save);
            service = new DefaultSchedulingService(repository);

            var busySlots = schedules.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().busySlots().stream()
                                    .map(slot -> Map.of("start", slot.start().toString(), "end", slot.end().toString()))
                                    .toList()
                    ));
            ctx.json(Map.of(
                    "participants", schedules.keySet().stream().sorted().toList(),
                    "busySlots", busySlots
            ));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void availability(Context ctx) {
        var body = ctx.bodyAsClass(AvailabilityRequest.class);
        var options = body.bufferMinutes() > 0
                ? SchedulingOptions.withBuffer(Duration.ofMinutes(body.bufferMinutes()))
                : SchedulingOptions.defaults();

        var slots = service.findAvailableSlots(body.participants(), Duration.ofMinutes(body.durationMinutes()), options);
        ctx.json(Map.of("slots", slots.stream().map(TimeSlot::start).map(Object::toString).toList()));
    }

    record AvailabilityRequest(List<String> participants, int durationMinutes, int bufferMinutes) {}

    private static class InMemoryRepository implements ScheduleRepository {
        private final Map<String, Schedule> data = new HashMap<>();
        public void save(Schedule s) { data.put(s.participantName(), s); }
        public Optional<Schedule> findByParticipant(String name) { return Optional.ofNullable(data.get(name)); }
        public Set<String> getAllParticipantNames() { return data.keySet(); }
        public void clear() { data.clear(); }
    }
}
