package io.slotify.contract;

public record CalendarEvent(String participantName, String subject, TimeSlot timeSlot) {

    public CalendarEvent {
        if (participantName == null || participantName.isBlank()) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR, "Participant name cannot be blank");
        }
        if (subject == null) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR, "Subject cannot be null");
        }
        if (timeSlot == null) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR, "Time slot cannot be null");
        }
        participantName = participantName.trim();
        subject = subject.trim();
    }
}
