package io.slotify.core;

import io.slotify.contract.TimeSlot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface CalendarParser {

    Map<String, Schedule> parseAndBuildSchedules(Path path);

    List<TimeSlot> parseBlackouts(Path path);
}
