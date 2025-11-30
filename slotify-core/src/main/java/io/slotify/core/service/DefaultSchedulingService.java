package io.slotify.core.service;

import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.AvailableSlot;
import io.slotify.core.model.Schedule;
import io.slotify.core.model.TimeSlot;
import io.slotify.core.repository.ScheduleRepository;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultSchedulingService implements SchedulingService {

    private static final LocalTime WORK_START = LocalTime.of(7, 0);
    private static final LocalTime WORK_END = LocalTime.of(19, 0);

    private final ScheduleRepository repository;
    private final List<TimeSlot> blackoutPeriods;
    private final Duration bufferBetweenMeetings;

    public DefaultSchedulingService(ScheduleRepository repository, List<TimeSlot> blackoutPeriods, Duration bufferBetweenMeetings) {
        if (repository == null) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "repository cannot be null");
        }
        if (bufferBetweenMeetings != null && bufferBetweenMeetings.isNegative()) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "Buffer cannot be negative");
        }
        this.repository = repository;
        this.blackoutPeriods = blackoutPeriods != null ? List.copyOf(blackoutPeriods) : List.of();
        this.bufferBetweenMeetings = bufferBetweenMeetings;
    }

    public DefaultSchedulingService(ScheduleRepository repository, List<TimeSlot> blackoutPeriods) {
        this(repository, blackoutPeriods, null);
    }

    public DefaultSchedulingService(ScheduleRepository repository) {
        this(repository, List.of(), null);
    }

    @Override
    public List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration) {
        return findAvailableSlots(participants, List.of(), meetingDuration)
                .stream()
                .map(AvailableSlot::timeSlot)
                .toList();
    }

    @Override
    public List<AvailableSlot> findAvailableSlots(List<String> requiredParticipants, List<String> optionalParticipants, Duration meetingDuration) {
        if (requiredParticipants == null || requiredParticipants.size() < 2) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "At least 2 required participants are needed for a meeting");
        }
        if (meetingDuration == null || meetingDuration.isZero() || meetingDuration.isNegative()) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_ARGUMENT, "Meeting duration must be positive");
        }
        var requiredBusy = collectBusySlots(requiredParticipants);
        var freeGaps = findGaps(requiredBusy);
        var baseSlots = generateAlignedSlots(freeGaps, meetingDuration);

        var optionalSchedules = optionalParticipants.stream()
                .map(name -> repository.findByParticipant(name).orElse(new Schedule(name, List.of())))
                .toList();

        return baseSlots.stream()
                .map(slot -> buildAvailableSlot(slot, optionalSchedules))
                .toList();
    }

    private List<TimeSlot> collectBusySlots(List<String> participants) {
        var allBusySlots = new ArrayList<TimeSlot>();

        for (var name : participants) {
            var schedule = repository.findByParticipant(name)
                    .orElseThrow(() -> new SchedulerException(
                            SchedulerException.ErrorType.PARTICIPANT_NOT_FOUND,
                            "Participant not found: " + name));

            schedule.busySlots().stream()
                    .map(this::applyBuffer)
                    .forEach(allBusySlots::add);
        }

        allBusySlots.addAll(blackoutPeriods);

        return TimeSlot.mergeOverlapping(allBusySlots);
    }

    private TimeSlot applyBuffer(TimeSlot slot) {
        return hasBuffer() ? slot.expandBy(bufferBetweenMeetings) : slot;
    }

    private boolean hasBuffer() {
        return bufferBetweenMeetings != null && !bufferBetweenMeetings.isZero();
    }

    private List<TimeSlot> findGaps(List<TimeSlot> busySlots) {
        var gaps = new ArrayList<TimeSlot>();
        var current = WORK_START;

        for (var busy : busySlots) {
            if (busy.end().isBefore(WORK_START) || busy.start().isAfter(WORK_END)) {
                continue;
            }

            var busyStart = TimeSlot.max(busy.start(), WORK_START);
            var busyEnd = TimeSlot.min(busy.end(), WORK_END);

            if (current.isBefore(busyStart)) {
                gaps.add(new TimeSlot(current, busyStart));
            }
            if (busyEnd.isAfter(current)) {
                current = busyEnd;
            }
        }

        if (current.isBefore(WORK_END)) {
            gaps.add(new TimeSlot(current, WORK_END));
        }

        return gaps;
    }

    private List<TimeSlot> generateAlignedSlots(List<TimeSlot> gaps, Duration meetingDuration) {
        var slots = new ArrayList<TimeSlot>();
        var slotIncrement = meetingDuration.toMinutes() <= 30 ? Duration.ofMinutes(30) : Duration.ofHours(1);

        for (var gap : gaps) {
            var slotStart = roundUpToSlot(gap.start(), slotIncrement);

            while (!slotStart.plus(meetingDuration).isAfter(gap.end())) {
                slots.add(new TimeSlot(slotStart, slotStart.plus(meetingDuration)));
                slotStart = slotStart.plus(slotIncrement);
            }
        }

        return slots;
    }

    private LocalTime roundUpToSlot(LocalTime time, Duration slotSize) {
        var slotMinutes = (int) slotSize.toMinutes();
        var totalMinutes = time.getHour() * 60 + time.getMinute();
        var remainder = totalMinutes % slotMinutes;
        if (remainder == 0) {
            return time;
        }
        return time.plusMinutes(slotMinutes - remainder);
    }

    private AvailableSlot buildAvailableSlot(TimeSlot slot, List<Schedule> optionalSchedules) {
        var effectiveSlot = applyBuffer(slot);

        var partitioned = optionalSchedules.stream()
                .collect(Collectors.partitioningBy(
                        schedule -> schedule.isBusyDuring(effectiveSlot),
                        Collectors.mapping(Schedule::participantName, Collectors.toList())
                ));

        return new AvailableSlot(slot, partitioned.get(false), partitioned.get(true));
    }
}
