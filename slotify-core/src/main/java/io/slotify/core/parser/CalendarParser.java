package io.slotify.core.parser;

import io.slotify.core.model.Schedule;
import io.slotify.core.model.TimeSlot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface CalendarParser {

    Map<String, Schedule> parseAndBuildSchedules(Path path);

    List<TimeSlot> parseBlackouts(Path path);
}
