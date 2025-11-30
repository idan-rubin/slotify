package io.slotify.core;

import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.Schedule;
import io.slotify.core.model.TimeSlot;
import io.slotify.core.repository.InMemoryScheduleRepository;
import io.slotify.core.service.DefaultSchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSchedulingServiceTest {

    private InMemoryScheduleRepository repository;
    private DefaultSchedulingService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryScheduleRepository();
        service = new DefaultSchedulingService(repository);
    }

    @Test
    void findAvailableSlots_withExampleScenario_returnsExpectedSlots() {
        // Alice: 8:00-9:30, 13:00-14:00, 16:00-17:00
        repository.save(new Schedule("Alice", List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 30)),
                new TimeSlot(LocalTime.of(13, 0), LocalTime.of(14, 0)),
                new TimeSlot(LocalTime.of(16, 0), LocalTime.of(17, 0))
        )));

        // Jack: 8:00-9:40, 13:00-14:00, 16:00-17:00
        repository.save(new Schedule("Jack", List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 40)),
                new TimeSlot(LocalTime.of(13, 0), LocalTime.of(14, 0)),
                new TimeSlot(LocalTime.of(16, 0), LocalTime.of(17, 0))
        )));

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
    void findAvailableSlots_withEmptySchedules_returnsAllHourlySlots() {
        repository.save(new Schedule("Alice", List.of()));
        repository.save(new Schedule("Jack", List.of()));

        var slots = service.findAvailableSlots(List.of("Alice", "Jack"), Duration.ofMinutes(60));

        assertThat(slots).hasSize(12); // 7:00 through 18:00
    }

    @Test
    void findAvailableSlots_withFullyBookedParticipant_returnsEmpty() {
        repository.save(new Schedule("Alice", List.of(
                new TimeSlot(LocalTime.of(7, 0), LocalTime.of(19, 0))
        )));

        var slots = service.findAvailableSlots(List.of("Alice"), Duration.ofMinutes(60));

        assertThat(slots).isEmpty();
    }

    @Test
    void findAvailableSlots_withUnknownParticipant_throwsException() {
        assertThatThrownBy(() -> service.findAvailableSlots(List.of("Unknown"), Duration.ofMinutes(60)))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.PARTICIPANT_NOT_FOUND);
    }

    @Test
    void findAvailableSlots_withNullParticipants_throwsException() {
        assertThatThrownBy(() -> service.findAvailableSlots(null, List.of(), Duration.ofMinutes(60)))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_ARGUMENT);
    }

    @Test
    void findAvailableSlots_withEmptyParticipants_throwsException() {
        assertThatThrownBy(() -> service.findAvailableSlots(List.of(), List.of(), Duration.ofMinutes(60)))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_ARGUMENT);
    }

    @Test
    void findAvailableSlots_withNullDuration_throwsException() {
        repository.save(new Schedule("Alice", List.of()));
        assertThatThrownBy(() -> service.findAvailableSlots(List.of("Alice"), List.of(), null))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_ARGUMENT);
    }

    @Test
    void findAvailableSlots_withZeroDuration_throwsException() {
        repository.save(new Schedule("Alice", List.of()));
        assertThatThrownBy(() -> service.findAvailableSlots(List.of("Alice"), List.of(), Duration.ZERO))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_ARGUMENT);
    }

    @Test
    void findAvailableSlots_withNegativeDuration_throwsException() {
        repository.save(new Schedule("Alice", List.of()));
        assertThatThrownBy(() -> service.findAvailableSlots(List.of("Alice"), List.of(), Duration.ofMinutes(-30)))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_ARGUMENT);
    }

    @Test
    void constructor_withInvalidBuffer_throwsException() {
        assertThatThrownBy(() -> new DefaultSchedulingService(repository, List.of(), Duration.ofMinutes(3)))
                .isInstanceOf(SchedulerException.class)
                .hasMessageContaining("between 5 and 15");
    }

    @Test
    void constructor_withNullRepository_throwsException() {
        assertThatThrownBy(() -> new DefaultSchedulingService(null))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_ARGUMENT);
    }

    @Test
    void findAvailableSlots_with30MinMeeting_findsMoreSlots() {
        repository.save(new Schedule("Alice", List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(8, 40))
        )));

        var slots = service.findAvailableSlots(List.of("Alice"), Duration.ofMinutes(30));

        assertThat(slots).isNotEmpty();
        var firstSlot = slots.getFirst();
        assertThat(Duration.between(firstSlot.start(), firstSlot.end())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void findAvailableSlots_withBuffer_expandsBusySlots() {
        repository.save(new Schedule("Alice", List.of(
                new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0))
        )));

        var serviceWithBuffer = new DefaultSchedulingService(repository, List.of(), Duration.ofMinutes(10));
        var slots = serviceWithBuffer.findAvailableSlots(List.of("Alice"), Duration.ofMinutes(60));

        // 9:00 slot should be blocked (would end at 10:00, needs 15 min buffer before 10:00 meeting)
        var startTimes = slots.stream().map(TimeSlot::start).toList();
        assertThat(startTimes).doesNotContain(LocalTime.of(9, 0));
    }

    @Test
    void findAvailableSlots_withBlackout_excludesBlackoutPeriod() {
        repository.save(new Schedule("Alice", List.of()));

        var blackouts = List.of(new TimeSlot(LocalTime.of(12, 0), LocalTime.of(13, 0)));
        var serviceWithBlackout = new DefaultSchedulingService(repository, blackouts);

        var slots = serviceWithBlackout.findAvailableSlots(List.of("Alice"), Duration.ofMinutes(60));

        var startTimes = slots.stream().map(TimeSlot::start).toList();
        assertThat(startTimes).doesNotContain(LocalTime.of(12, 0));
    }

    @Test
    void findAvailableSlots_withOptionalParticipants_returnsAvailabilityInfo() {
        repository.save(new Schedule("Alice", List.of()));
        repository.save(new Schedule("Jack", List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0))
        )));
        repository.save(new Schedule("Bob", List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(12, 0))
        )));

        var slots = service.findAvailableSlots(
                List.of("Alice"),
                List.of("Jack", "Bob"),
                Duration.ofMinutes(60));

        // At 9:00, both Jack and Bob are busy
        var slot9 = slots.stream()
                .filter(s -> s.timeSlot().start().equals(LocalTime.of(9, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot9.unavailableOptionalParticipants()).containsExactlyInAnyOrder("Jack", "Bob");

        // At 10:00, only Bob is busy
        var slot10 = slots.stream()
                .filter(s -> s.timeSlot().start().equals(LocalTime.of(10, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot10.availableOptionalParticipants()).contains("Jack");
        assertThat(slot10.unavailableOptionalParticipants()).contains("Bob");

        // At 12:00, both are free
        var slot12 = slots.stream()
                .filter(s -> s.timeSlot().start().equals(LocalTime.of(12, 0)))
                .findFirst()
                .orElseThrow();
        assertThat(slot12.availableOptionalParticipants()).containsExactlyInAnyOrder("Jack", "Bob");
    }

}
