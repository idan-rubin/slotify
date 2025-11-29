package io.slotify.core;

import io.slotify.contract.CalendarEvent;
import io.slotify.contract.SchedulerException;
import io.slotify.contract.TimeSlot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CsvCalendarParser implements CalendarParser {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    @Override
    public Map<String, Schedule> parseAndBuildSchedules(Path csvPath) {
        var events = parseCsv(csvPath, 4, this::parseEventLine);
        return events.stream()
                .collect(Collectors.groupingBy(CalendarEvent::participantName))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Schedule.fromEvents(e.getKey(), e.getValue())
                ));
    }

    @Override
    public List<TimeSlot> parseBlackouts(Path blackoutPath) {
        if (!Files.exists(blackoutPath)) {
            return List.of();
        }
        return parseCsv(blackoutPath, 2, this::parseBlackoutLine);
    }

    private <T> List<T> parseCsv(Path path, int expectedColumns, LineParser<T> parser) {
        try {
            var lines = Files.readAllLines(path);
            var results = new ArrayList<T>();

            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i).trim();
                if (line.isEmpty()) continue;

                var parts = line.split(",");
                if (parts.length != expectedColumns) {
                    throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                            "Invalid format at line %d: expected %d columns, got %d"
                                    .formatted(i + 1, expectedColumns, parts.length));
                }
                results.add(parser.parse(parts, i + 1));
            }

            return List.copyOf(results);
        } catch (IOException e) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                    "Failed to read file: " + path, e);
        }
    }

    private CalendarEvent parseEventLine(String[] parts, int lineNumber) {
        var participant = parts[0].trim();
        var subject = parts[1].trim();
        var start = parseTime(parts[2].trim(), lineNumber);
        var end = parseTime(parts[3].trim(), lineNumber);
        return new CalendarEvent(participant, subject, new TimeSlot(start, end));
    }

    private TimeSlot parseBlackoutLine(String[] parts, int lineNumber) {
        var start = parseTime(parts[0].trim(), lineNumber);
        var end = parseTime(parts[1].trim(), lineNumber);
        return new TimeSlot(start, end);
    }

    private LocalTime parseTime(String timeStr, int lineNumber) {
        try {
            return LocalTime.parse(timeStr, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                    "Invalid time format '%s' at line %d".formatted(timeStr, lineNumber));
        }
    }

    @FunctionalInterface
    private interface LineParser<T> {
        T parse(String[] parts, int lineNumber);
    }
}
