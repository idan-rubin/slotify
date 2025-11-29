package io.slotify;

import io.slotify.contract.SchedulerException;
import io.slotify.contract.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

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
}
