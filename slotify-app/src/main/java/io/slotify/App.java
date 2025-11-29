package io.slotify;

import io.slotify.contract.SchedulingOptions;
import io.slotify.contract.SchedulingService;
import io.slotify.contract.TimeSlot;
import io.slotify.core.CalendarParser;
import io.slotify.core.CsvCalendarParser;
import io.slotify.core.DefaultSchedulingService;
import io.slotify.core.RedisScheduleRepository;
import io.slotify.core.ScheduleRepository;
import redis.clients.jedis.JedisPool;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        var calendarPath = Path.of("src/main/resources/calendar.csv");
        var blackoutPath = Path.of("src/main/resources/blackout.csv");

        try (var jedisPool = new JedisPool("localhost", 6379)) {
            ScheduleRepository repository = new RedisScheduleRepository(jedisPool);
            CalendarParser parser = new CsvCalendarParser();

            repository.clear();
            var schedules = parser.parseAndBuildSchedules(calendarPath);
            schedules.values().forEach(repository::save);
            LOG.info("Loaded %d participants".formatted(schedules.size()));

            var blackouts = parser.parseBlackouts(blackoutPath);
            LOG.info("Loaded %d blackout periods".formatted(blackouts.size()));

            SchedulingService service = new DefaultSchedulingService(repository, blackouts);

            // Example: Find slots for Alice and Jack (60 min meeting)
            var participants = List.of("Alice", "Jack");
            var duration = Duration.ofMinutes(60);

            System.out.println("\n=== Available slots for Alice + Jack (60 min) ===");
            var slots = service.findAvailableSlots(participants, duration);
            printSlots(slots);

            // Example with buffer
            System.out.println("\n=== With 15 min buffer between meetings ===");
            var slotsWithBuffer = service.findAvailableSlots(participants, duration,
                    SchedulingOptions.withBuffer(Duration.ofMinutes(15)));
            printSlots(slotsWithBuffer);

            // Example with optional participants
            System.out.println("\n=== Alice required, Jack + Bob optional ===");
            var advancedSlots = service.findAvailableSlots(
                    List.of("Alice"),
                    List.of("Jack", "Bob"),
                    duration);

            for (var slot : advancedSlots) {
                System.out.println("%s - available: %s, unavailable: %s".formatted(
                        slot.timeSlot().start(),
                        slot.availableOptionalParticipants(),
                        slot.unavailableOptionalParticipants()));
            }
        }
    }

    private static void printSlots(List<TimeSlot> slots) {
        var times = slots.stream()
                .map(s -> s.start().toString())
                .toList();
        System.out.println(String.join(", ", times));
    }
}
