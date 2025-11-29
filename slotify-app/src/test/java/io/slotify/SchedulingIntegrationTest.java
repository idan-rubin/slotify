package io.slotify;

import io.slotify.contract.SchedulingOptions;
import io.slotify.contract.TimeSlot;
import io.slotify.core.CsvCalendarParser;
import io.slotify.core.DefaultSchedulingService;
import io.slotify.core.InMemoryScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingIntegrationTest {

    @TempDir
    Path tempDir;

    private InMemoryScheduleRepository repository;
    private CsvCalendarParser parser;

    @BeforeEach
    void setUp() {
        repository = new InMemoryScheduleRepository();
        parser = new CsvCalendarParser();
    }

    @Test
    void endToEnd_withAssignmentExample_returnsExpectedSlots() throws IOException {
        var csv = """
                Alice,Team standup,8:00,8:30
                Alice,Project review,8:30,9:30
                Alice,Lunch with client,13:00,14:00
                Alice,Interview,16:00,17:00
                Jack,Team standup,8:00,8:50
                Jack,Code review,9:00,9:40
                Jack,Sprint planning,13:00,14:00
                Jack,One on one,16:00,17:00
                """;
        var path = createTempFile("calendar.csv", csv);

        var schedules = parser.parseAndBuildSchedules(path);
        schedules.values().forEach(repository::save);

        var service = new DefaultSchedulingService(repository);
        var slots = service.findAvailableSlots(List.of("Alice", "Jack"), Duration.ofMinutes(60));

        var startTimes = slots.stream().map(TimeSlot::start).toList();
        assertThat(startTimes).containsExactly(
                LocalTime.of(7, 0),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                LocalTime.of(17, 0),
                LocalTime.of(18, 0)
        );
    }

    @Test
    void endToEnd_withBlackouts_excludesLunchHour() throws IOException {
        var csv = """
                Alice,Morning meeting,8:00,9:00
                """;
        var blackouts = "12:00,13:00";

        var calendarPath = createTempFile("calendar.csv", csv);
        var blackoutPath = createTempFile("blackout.csv", blackouts);

        var schedules = parser.parseAndBuildSchedules(calendarPath);
        schedules.values().forEach(repository::save);
        var blackoutSlots = parser.parseBlackouts(blackoutPath);

        var service = new DefaultSchedulingService(repository, blackoutSlots);
        var slots = service.findAvailableSlots(List.of("Alice"), Duration.ofMinutes(60));

        var startTimes = slots.stream().map(TimeSlot::start).toList();
        assertThat(startTimes).doesNotContain(LocalTime.of(12, 0));
    }

    @Test
    void endToEnd_withBufferTime_reducesAvailability() throws IOException {
        var csv = """
                Alice,Meeting,10:00,11:00
                """;
        var path = createTempFile("calendar.csv", csv);

        var schedules = parser.parseAndBuildSchedules(path);
        schedules.values().forEach(repository::save);

        var service = new DefaultSchedulingService(repository);

        // Without buffer
        var slotsNoBuffer = service.findAvailableSlots(List.of("Alice"), Duration.ofMinutes(60));
        assertThat(slotsNoBuffer.stream().map(TimeSlot::start).toList())
                .contains(LocalTime.of(9, 0));

        // With 15 min buffer - 9:00 slot should be blocked
        var slotsWithBuffer = service.findAvailableSlots(
                List.of("Alice"),
                Duration.ofMinutes(60),
                SchedulingOptions.withBuffer(Duration.ofMinutes(15)));
        assertThat(slotsWithBuffer.stream().map(TimeSlot::start).toList())
                .doesNotContain(LocalTime.of(9, 0));
    }

    @Test
    void endToEnd_withOptionalParticipants_showsAvailability() throws IOException {
        var csv = """
                Alice,Meeting,8:00,9:00
                Jack,Meeting,9:00,10:00
                Bob,Meeting,9:00,12:00
                """;
        var path = createTempFile("calendar.csv", csv);

        var schedules = parser.parseAndBuildSchedules(path);
        schedules.values().forEach(repository::save);

        var service = new DefaultSchedulingService(repository);
        var slots = service.findAvailableSlots(
                List.of("Alice"),
                List.of("Jack", "Bob"),
                Duration.ofMinutes(60));

        // At 9:00, both optional are busy
        var slot9 = slots.stream()
                .filter(s -> s.timeSlot().start().equals(LocalTime.of(9, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot9.availableOptionalParticipants()).isEmpty();

        // At 10:00, Jack is free, Bob is busy
        var slot10 = slots.stream()
                .filter(s -> s.timeSlot().start().equals(LocalTime.of(10, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot10.availableOptionalParticipants()).containsExactly("Jack");
        assertThat(slot10.unavailableOptionalParticipants()).containsExactly("Bob");

        // At 12:00, both are free
        var slot12 = slots.stream()
                .filter(s -> s.timeSlot().start().equals(LocalTime.of(12, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot12.availableOptionalParticipants()).hasSize(2);
    }

    private Path createTempFile(String name, String content) throws IOException {
        var path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
