package io.slotify.core.repository;

import io.slotify.core.model.Schedule;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryScheduleRepository implements ScheduleRepository {

    private final Map<String, Schedule> data = new ConcurrentHashMap<>();

    @Override
    public void save(Schedule schedule) {
        data.put(schedule.participantName(), schedule);
    }

    @Override
    public Optional<Schedule> findByParticipant(String name) {
        return Optional.ofNullable(data.get(name));
    }

    @Override
    public Set<String> getAllParticipantNames() {
        return Set.copyOf(data.keySet());
    }

    @Override
    public void clear() {
        data.clear();
    }
}
