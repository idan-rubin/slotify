package io.slotify.core.model;

import io.slotify.core.exception.SchedulerException;

public record CalendarEvent(String participantName, String subject, TimeSlot timeSlot) {

    public CalendarEvent {
        if (participantName == null || participantName.isBlank()) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "Participant name cannot be blank");
        }
        if (subject == null) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "Subject cannot be null");
        }
        if (timeSlot == null) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "Time slot cannot be null");
        }
        participantName = participantName.trim();
        subject = subject.trim();
    }
}
