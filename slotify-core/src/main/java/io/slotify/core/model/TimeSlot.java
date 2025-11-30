package io.slotify.core.model;

import io.slotify.core.exception.SchedulerException;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    public static List<TimeSlot> mergeOverlapping(List<TimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }

        var sorted = slots.stream()
                .sorted(Comparator.comparing(TimeSlot::start))
                .toList();

        var merged = new ArrayList<TimeSlot>();
        var current = sorted.getFirst();

        for (int i = 1; i < sorted.size(); i++) {
            var next = sorted.get(i);

            if (!next.start().isAfter(current.end())) {
                var newEnd = max(current.end(), next.end());
                current = new TimeSlot(current.start(), newEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return List.copyOf(merged);
    }

    public static LocalTime max(LocalTime first, LocalTime second) {
        return first.isAfter(second) ? first : second;
    }

    public static LocalTime min(LocalTime first, LocalTime second) {
        return first.isBefore(second) ? first : second;
    }
}
