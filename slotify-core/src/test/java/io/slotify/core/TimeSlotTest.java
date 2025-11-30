package io.slotify.core;

import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    @Test
    void constructor_withValidTimes_createsSlot() {
        var slot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThat(slot.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slot.end()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void constructor_withEndBeforeStart_throwsException() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(10, 0), LocalTime.of(9, 0)))
                .isInstanceOf(SchedulerException.class)
                .extracting(e -> ((SchedulerException) e).getErrorType())
                .isEqualTo(SchedulerException.ErrorType.INVALID_TIME_RANGE);
    }

    @Test
    void constructor_withEqualTimes_throwsException() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(SchedulerException.class);
    }

    @Test
    void overlaps_withOverlappingSlots_returnsTrue() {
        var slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
        var slot2 = new TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 30));

        assertThat(slot1.overlaps(slot2)).isTrue();
        assertThat(slot2.overlaps(slot1)).isTrue();
    }

    @Test
    void overlaps_withAdjacentSlots_returnsFalse() {
        var slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
        var slot2 = new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(slot1.overlaps(slot2)).isFalse();
    }

    @Test
    void overlaps_withNonOverlappingSlots_returnsFalse() {
        var slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
        var slot2 = new TimeSlot(LocalTime.of(11, 0), LocalTime.of(12, 0));

        assertThat(slot1.overlaps(slot2)).isFalse();
    }

    @Test
    void expandBy_withBuffer_expandsBothEnds() {
        var slot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));

        var expanded = slot.expandBy(Duration.ofMinutes(15));

        assertThat(expanded.start()).isEqualTo(LocalTime.of(8, 45));
        assertThat(expanded.end()).isEqualTo(LocalTime.of(10, 15));
    }

    @Test
    void expandBy_withNullBuffer_returnsSameSlot() {
        var slot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));

        var expanded = slot.expandBy(null);

        assertThat(expanded).isEqualTo(slot);
    }

    @Test
    void expandBy_withZeroBuffer_returnsSameSlot() {
        var slot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));

        var expanded = slot.expandBy(Duration.ZERO);

        assertThat(expanded).isEqualTo(slot);
    }

    @Test
    void toString_formatsCorrectly() {
        var slot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 30));

        assertThat(slot.toString()).isEqualTo("09:00-10:30");
    }

    @Test
    void mergeOverlapping_withOverlap_mergesCorrectly() {
        var slots = List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new TimeSlot(LocalTime.of(8, 30), LocalTime.of(10, 0)),
                new TimeSlot(LocalTime.of(14, 0), LocalTime.of(15, 0))
        );

        var merged = TimeSlot.mergeOverlapping(slots);

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0)).isEqualTo(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertThat(merged.get(1)).isEqualTo(new TimeSlot(LocalTime.of(14, 0), LocalTime.of(15, 0)));
    }

    @Test
    void mergeOverlapping_withAdjacentSlots_mergesCorrectly() {
        var slots = List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0))
        );

        var merged = TimeSlot.mergeOverlapping(slots);

        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst()).isEqualTo(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(11, 0)));
    }

    @Test
    void mergeOverlapping_withNoOverlap_keepsAll() {
        var slots = List.of(
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0))
        );

        var merged = TimeSlot.mergeOverlapping(slots);

        assertThat(merged).hasSize(2);
    }

    @Test
    void mergeOverlapping_withEmptyList_returnsEmpty() {
        assertThat(TimeSlot.mergeOverlapping(List.of())).isEmpty();
    }
}
