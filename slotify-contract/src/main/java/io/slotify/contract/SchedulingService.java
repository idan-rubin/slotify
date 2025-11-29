package io.slotify.contract;

import java.time.Duration;
import java.util.List;

public interface SchedulingService {

    List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration);

    List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration, SchedulingOptions options);

    List<AvailableSlot> findAvailableSlots(List<String> requiredParticipants, List<String> optionalParticipants, Duration meetingDuration);

    List<AvailableSlot> findAvailableSlots(List<String> requiredParticipants, List<String> optionalParticipants, Duration meetingDuration, SchedulingOptions options);
}
