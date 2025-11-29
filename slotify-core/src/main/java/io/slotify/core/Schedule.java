package io.slotify.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.slotify.contract.CalendarEvent;
import io.slotify.contract.TimeSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Schedule(String participantName, List<TimeSlot> busySlots) {

    @JsonCreator
    public Schedule(
            @JsonProperty("participantName") String participantName,
            @JsonProperty("busySlots") List<TimeSlot> busySlots) {
        this.participantName = java.util.Objects.requireNonNull(participantName);
        this.busySlots = busySlots != null ? List.copyOf(busySlots) : List.of();
    }

    public static Schedule fromEvents(String participantName, List<CalendarEvent> events) {
        var slots = events.stream()
                .map(CalendarEvent::timeSlot)
                .toList();
        return new Schedule(participantName, mergeOverlappingSlots(slots));
    }

    public boolean isBusyDuring(TimeSlot timeSlot) {
        return busySlots.stream().anyMatch(slot -> slot.overlaps(timeSlot));
    }

    public static List<TimeSlot> mergeOverlappingSlots(List<TimeSlot> slots) {
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
                var newEnd = current.end().isAfter(next.end()) ? current.end() : next.end();
                current = new TimeSlot(current.start(), newEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return List.copyOf(merged);
    }
}
