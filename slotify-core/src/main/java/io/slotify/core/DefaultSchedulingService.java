package io.slotify.core;

import io.slotify.contract.AvailableSlot;
import io.slotify.contract.SchedulerException;
import io.slotify.contract.SchedulingOptions;
import io.slotify.contract.SchedulingService;
import io.slotify.contract.TimeSlot;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class DefaultSchedulingService implements SchedulingService {

    private static final LocalTime WORK_START = LocalTime.of(7, 0);
    private static final LocalTime WORK_END = LocalTime.of(19, 0);

    private final ScheduleRepository repository;
    private final List<TimeSlot> blackoutPeriods;

    public DefaultSchedulingService(ScheduleRepository repository, List<TimeSlot> blackoutPeriods) {
        this.repository = repository;
        this.blackoutPeriods = blackoutPeriods != null ? List.copyOf(blackoutPeriods) : List.of();
    }

    public DefaultSchedulingService(ScheduleRepository repository) {
        this(repository, List.of());
    }

    @Override
    public List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration) {
        return findAvailableSlots(participants, meetingDuration, SchedulingOptions.defaults());
    }

    @Override
    public List<TimeSlot> findAvailableSlots(List<String> participants, Duration meetingDuration, SchedulingOptions options) {
        var busySlots = collectBusySlots(participants, options);
        var freeGaps = findGaps(busySlots);
        return generateHourlySlots(freeGaps, meetingDuration);
    }

    @Override
    public List<AvailableSlot> findAvailableSlots(List<String> requiredParticipants, List<String> optionalParticipants, Duration meetingDuration) {
        return findAvailableSlots(requiredParticipants, optionalParticipants, meetingDuration, SchedulingOptions.defaults());
    }

    @Override
    public List<AvailableSlot> findAvailableSlots(List<String> requiredParticipants, List<String> optionalParticipants, Duration meetingDuration, SchedulingOptions options) {
        var requiredBusy = collectBusySlots(requiredParticipants, options);
        var freeGaps = findGaps(requiredBusy);
        var baseSlots = generateHourlySlots(freeGaps, meetingDuration);

        var optionalSchedules = optionalParticipants.stream()
                .map(name -> repository.findByParticipant(name).orElse(new Schedule(name, List.of())))
                .toList();

        return baseSlots.stream()
                .map(slot -> buildAvailableSlot(slot, optionalSchedules, options))
                .toList();
    }

    private List<TimeSlot> collectBusySlots(List<String> participants, SchedulingOptions options) {
        var allBusySlots = new ArrayList<TimeSlot>();

        for (var name : participants) {
            var schedule = repository.findByParticipant(name)
                    .orElseThrow(() -> new SchedulerException(
                            SchedulerException.ErrorType.PARTICIPANT_NOT_FOUND,
                            "Participant not found: " + name));

            if (options.hasBuffer()) {
                schedule.busySlots().stream()
                        .map(slot -> slot.expandBy(options.bufferBetweenMeetings()))
                        .forEach(allBusySlots::add);
            } else {
                allBusySlots.addAll(schedule.busySlots());
            }
        }

        allBusySlots.addAll(blackoutPeriods);

        return Schedule.mergeOverlappingSlots(allBusySlots);
    }

    private List<TimeSlot> findGaps(List<TimeSlot> busySlots) {
        var gaps = new ArrayList<TimeSlot>();
        var current = WORK_START;

        for (var busy : busySlots) {
            if (busy.end().isBefore(WORK_START) || busy.start().isAfter(WORK_END)) {
                continue;
            }

            var busyStart = busy.start().isBefore(WORK_START) ? WORK_START : busy.start();
            var busyEnd = busy.end().isAfter(WORK_END) ? WORK_END : busy.end();

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

    private List<TimeSlot> generateHourlySlots(List<TimeSlot> gaps, Duration meetingDuration) {
        var slots = new ArrayList<TimeSlot>();

        for (var gap : gaps) {
            var slotStart = roundUpToHour(gap.start());

            while (!slotStart.plus(meetingDuration).isAfter(gap.end())) {
                slots.add(new TimeSlot(slotStart, slotStart.plus(meetingDuration)));
                slotStart = slotStart.plusHours(1);
            }
        }

        return slots;
    }

    private LocalTime roundUpToHour(LocalTime time) {
        if (time.getMinute() == 0 && time.getSecond() == 0) {
            return time;
        }
        return time.plusHours(1).withMinute(0).withSecond(0).withNano(0);
    }

    private AvailableSlot buildAvailableSlot(TimeSlot slot, List<Schedule> optionalSchedules, SchedulingOptions options) {
        var effectiveSlot = options.hasBuffer()
                ? slot.expandBy(options.bufferBetweenMeetings())
                : slot;

        var available = new ArrayList<String>();
        var unavailable = new ArrayList<String>();

        for (var schedule : optionalSchedules) {
            if (schedule.isBusyDuring(effectiveSlot)) {
                unavailable.add(schedule.participantName());
            } else {
                available.add(schedule.participantName());
            }
        }

        return new AvailableSlot(slot, available, unavailable);
    }
}
