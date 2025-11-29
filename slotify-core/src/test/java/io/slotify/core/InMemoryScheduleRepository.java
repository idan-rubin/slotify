package io.slotify.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InMemoryScheduleRepository implements ScheduleRepository {

    private final Map<String, Schedule> schedules = new HashMap<>();

    @Override
    public void save(Schedule schedule) {
        schedules.put(schedule.participantName(), schedule);
    }

    @Override
    public Optional<Schedule> findByParticipant(String name) {
        return Optional.ofNullable(schedules.get(name));
    }

    @Override
    public Set<String> getAllParticipantNames() {
        return schedules.keySet();
    }

    @Override
    public void clear() {
        schedules.clear();
    }
}
