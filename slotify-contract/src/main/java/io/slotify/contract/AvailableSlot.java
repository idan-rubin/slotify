package io.slotify.contract;

import java.util.List;
import java.util.Objects;

public record AvailableSlot(
        TimeSlot timeSlot,
        List<String> availableOptionalParticipants,
        List<String> unavailableOptionalParticipants) {

    public AvailableSlot {
        Objects.requireNonNull(timeSlot, "timeSlot cannot be null");
        availableOptionalParticipants = availableOptionalParticipants != null
                ? List.copyOf(availableOptionalParticipants)
                : List.of();
        unavailableOptionalParticipants = unavailableOptionalParticipants != null
                ? List.copyOf(unavailableOptionalParticipants)
                : List.of();
    }

}
