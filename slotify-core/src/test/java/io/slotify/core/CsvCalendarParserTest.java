package io.slotify.core;

import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.TimeSlot;
import io.slotify.core.parser.CsvCalendarParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCalendarParserTest {

    @TempDir
    Path tempDir;

    private CsvCalendarParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvCalendarParser();
    }

    @Test
    void parseAndBuildSchedules_withValidCsv_parsesCorrectly() throws IOException {
        var csv = """
                Alice,Meeting,8:00,9:00
                Alice,Standup,9:30,10:00
                Jack,Review,8:00,8:50
                """;
        var path = createTempFile("calendar.csv", csv);

        var schedules = parser.parseAndBuildSchedules(path);

        assertThat(schedules).hasSize(2);
        assertThat(schedules.get("Alice").busySlots()).hasSize(2);
        assertThat(schedules.get("Jack").busySlots()).hasSize(1);
    }

    @Test
    void parseAndBuildSchedules_mergesOverlappingSlots() throws IOException {
        var csv = """
                Alice,Meeting 1,8:00,9:00
                Alice,Meeting 2,8:30,10:00
                """;
        var path = createTempFile("calendar.csv", csv);

        var schedules = parser.parseAndBuildSchedules(path);

        assertThat(schedules.get("Alice").busySlots()).hasSize(1);
        assertThat(schedules.get("Alice").busySlots().getFirst())
                .isEqualTo(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(10, 0)));
    }

    @Test
    void parseAndBuildSchedules_withInvalidFormat_throwsException() throws IOException {
        var csv = "Alice,Meeting,8:00";
        var path = createTempFile("calendar.csv", csv);

        assertThatThrownBy(() -> parser.parseAndBuildSchedules(path))
                .isInstanceOf(SchedulerException.class)
                .hasMessageContaining("expected 4 columns");
    }

    @Test
    void parseAndBuildSchedules_withInvalidTime_throwsException() throws IOException {
        var csv = "Alice,Meeting,invalid,9:00";
        var path = createTempFile("calendar.csv", csv);

        assertThatThrownBy(() -> parser.parseAndBuildSchedules(path))
                .isInstanceOf(SchedulerException.class)
                .hasMessageContaining("Invalid time format");
    }

    @Test
    void parseBlackouts_withValidFile_parsesCorrectly() throws IOException {
        var blackouts = """
                12:00,13:00
                18:00,19:00
                """;
        var path = createTempFile("blackout.csv", blackouts);

        var result = parser.parseBlackouts(path);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new TimeSlot(LocalTime.of(12, 0), LocalTime.of(13, 0)));
        assertThat(result.get(1)).isEqualTo(new TimeSlot(LocalTime.of(18, 0), LocalTime.of(19, 0)));
    }

    @Test
    void parseBlackouts_withMissingFile_returnsEmpty() {
        var path = tempDir.resolve("nonexistent.csv");

        var result = parser.parseBlackouts(path);

        assertThat(result).isEmpty();
    }

    @Test
    void parseBlackouts_withEmptyFile_returnsEmpty() throws IOException {
        var path = createTempFile("blackout.csv", "");

        var result = parser.parseBlackouts(path);

        assertThat(result).isEmpty();
    }

    private Path createTempFile(String name, String content) throws IOException {
        var path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
