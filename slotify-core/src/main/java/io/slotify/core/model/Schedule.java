package io.slotify.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record Schedule(String participantName, List<TimeSlot> busySlots) {

    @JsonCreator
    public Schedule(
            @JsonProperty("participantName") String participantName,
            @JsonProperty("busySlots") List<TimeSlot> busySlots) {
        this.participantName = Objects.requireNonNull(participantName);
        this.busySlots = busySlots != null ? List.copyOf(busySlots) : List.of();
    }

    public static Schedule fromEvents(String participantName, List<CalendarEvent> events) {
        var slots = events.stream()
                .map(CalendarEvent::timeSlot)
                .toList();
        return new Schedule(participantName, TimeSlot.mergeOverlapping(slots));
    }

    public boolean isBusyDuring(TimeSlot timeSlot) {
        return busySlots.stream().anyMatch(slot -> slot.overlaps(timeSlot));
    }
}
