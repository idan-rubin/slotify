package io.slotify.core.parser;

import io.slotify.core.model.Constants;
import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.CalendarEvent;
import io.slotify.core.model.Schedule;
import io.slotify.core.model.TimeSlot;

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
    private static final int MAX_LINE_LENGTH = 2000;
    private static final int MAX_LINES = 10000;
    private static final int MAX_SUBJECT_LENGTH = 500;

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
            if (lines.size() > MAX_LINES) {
                throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                        "File has too many lines (max %d)".formatted(MAX_LINES));
            }
            var results = new ArrayList<T>();

            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                if (line.length() > MAX_LINE_LENGTH) {
                    throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                            "Line %d is too long (max %d characters)".formatted(i + 1, MAX_LINE_LENGTH));
                }
                line = line.trim();
                if (line.isEmpty()) continue;

                var parts = parseCsvLine(line);
                if (parts.size() != expectedColumns) {
                    throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                            "Invalid format at line %d: expected %d columns, got %d"
                                    .formatted(i + 1, expectedColumns, parts.size()));
                }
                results.add(parser.parse(parts.toArray(new String[0]), i + 1));
            }

            return List.copyOf(results);
        } catch (IOException e) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                    "Failed to read file: " + path, e);
        }
    }

    private List<String> parseCsvLine(String line) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (inQuotes) {
                    inQuotes = false;
                } else if (!fieldStarted || current.length() == 0) {
                    inQuotes = true;
                    fieldStarted = true;
                } else {
                    current.append(c);
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
                fieldStarted = false;
            } else {
                current.append(c);
                fieldStarted = true;
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private CalendarEvent parseEventLine(String[] parts, int lineNumber) {
        var participant = parts[0].trim();
        if (participant.isEmpty()) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                    "Empty participant name at line %d".formatted(lineNumber));
        }
        if (participant.length() > Constants.MAX_NAME_LENGTH) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                    "Participant name too long at line %d (max %d characters)".formatted(lineNumber, Constants.MAX_NAME_LENGTH));
        }
        var subject = parts[1].trim();
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new SchedulerException(SchedulerException.ErrorType.PARSE_ERROR,
                    "Subject too long at line %d (max %d characters)".formatted(lineNumber, MAX_SUBJECT_LENGTH));
        }
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
