package io.slotify.core.service;

import io.slotify.core.model.AvailableSlot;
import io.slotify.core.model.TimeSlot;

import java.time.Duration;
import java.util.List;

public interface SchedulingService {

    List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration);

    List<AvailableSlot> findAvailableSlots(List<String> requiredParticipants, List<String> optionalParticipants, Duration meetingDuration);
}
