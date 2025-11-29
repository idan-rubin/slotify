package io.slotify.core;

import java.util.Optional;
import java.util.Set;

public interface ScheduleRepository {

    void save(Schedule schedule);

    Optional<Schedule> findByParticipant(String name);

    Set<String> getAllParticipantNames();

    void clear();
}
