package io.slotify.contract;

import java.time.Duration;
import java.time.LocalTime;

public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_TIME_RANGE, "Start and end times cannot be null");
        }
        if (!end.isAfter(start)) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_TIME_RANGE,
                    "End time %s must be after start time %s".formatted(end, start));
        }
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public TimeSlot expandBy(Duration buffer) {
        if (buffer == null || buffer.isZero()) {
            return this;
        }

        var newStart = start.minus(buffer);
        var newEnd = end.plus(buffer);

        if (newStart.isAfter(start)) newStart = LocalTime.MIN;
        if (newEnd.isBefore(end)) newEnd = LocalTime.MAX;

        return new TimeSlot(newStart, newEnd);
    }

    @Override
    public String toString() {
        return "%s-%s".formatted(start, end);
    }
}
