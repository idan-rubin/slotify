package io.slotify.core;

import io.slotify.contract.CalendarEvent;
import io.slotify.contract.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleTest {

    @Test
    void mergeOverlappingSlots_withOverlap_mergesCorrectly() {
        var slots = List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new TimeSlot(LocalTime.of(8, 30), LocalTime.of(10, 0)),
                new TimeSlot(LocalTime.of(14, 0), LocalTime.of(15, 0))
        );

        var merged = Schedule.mergeOverlappingSlots(slots);

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0)).isEqualTo(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertThat(merged.get(1)).isEqualTo(new TimeSlot(LocalTime.of(14, 0), LocalTime.of(15, 0)));
    }

    @Test
    void mergeOverlappingSlots_withAdjacentSlots_mergesCorrectly() {
        var slots = List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0))
        );

        var merged = Schedule.mergeOverlappingSlots(slots);

        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst()).isEqualTo(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(11, 0)));
    }

    @Test
    void mergeOverlappingSlots_withNoOverlap_keepsAll() {
        var slots = List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0))
        );

        var merged = Schedule.mergeOverlappingSlots(slots);

        assertThat(merged).hasSize(2);
    }

    @Test
    void mergeOverlappingSlots_withEmptyList_returnsEmpty() {
        assertThat(Schedule.mergeOverlappingSlots(List.of())).isEmpty();
    }

    @Test
    void fromEvents_mergesBusySlots() {
        var events = List.of(
                new CalendarEvent("Alice", "Meeting 1", new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 0))),
                new CalendarEvent("Alice", "Meeting 2", new TimeSlot(LocalTime.of(8, 30), LocalTime.of(10, 0)))
        );

        var schedule = Schedule.fromEvents("Alice", events);

        assertThat(schedule.participantName()).isEqualTo("Alice");
        assertThat(schedule.busySlots()).hasSize(1);
        assertThat(schedule.busySlots().getFirst()).isEqualTo(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(10, 0)));
    }

    @Test
    void isBusyDuring_withOverlappingSlot_returnsTrue() {
        var schedule = new Schedule("Alice", List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0))
        ));

        var testSlot = new TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 30));
        assertThat(schedule.isBusyDuring(testSlot)).isTrue();
    }

    @Test
    void isBusyDuring_withNonOverlappingSlot_returnsFalse() {
        var schedule = new Schedule("Alice", List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0))
        ));

        var testSlot = new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(schedule.isBusyDuring(testSlot)).isFalse();
    }
}
