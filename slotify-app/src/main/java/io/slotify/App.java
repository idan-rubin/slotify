package io.slotify;

import io.slotify.core.model.AvailableSlot;
import io.slotify.core.model.TimeSlot;
import io.slotify.core.parser.CsvCalendarParser;
import io.slotify.core.repository.InMemoryScheduleRepository;
import io.slotify.core.service.DefaultSchedulingService;
import io.slotify.core.service.SchedulingService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(name = "slotify", mixinStandardHelpOptions = true, version = "1.0",
         description = "Find the perfect meeting slot for everyone")
public class App implements Callable<Integer> {

    @Parameters(description = "Calendar CSV file (use '-' for stdin)")
    private String calendarInput;

    @Option(names = {"-b", "--blackout"}, description = "Blackout periods CSV file")
    private Path blackoutPath;

    @Option(names = {"--buffer"}, description = "Buffer minutes between meetings", defaultValue = "0")
    private int bufferMinutes;

    public static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args));
    }

    @Override
    public Integer call() throws IOException {
        var parser = new CsvCalendarParser();
        var repository = new InMemoryScheduleRepository();

        Path calendarPath;
        if ("-".equals(calendarInput)) {
            calendarPath = Files.createTempFile("calendar", ".csv");
            Files.write(calendarPath, System.in.readAllBytes());
            calendarPath.toFile().deleteOnExit();
        } else {
            calendarPath = Path.of(calendarInput);
        }

        var schedules = parser.parseAndBuildSchedules(calendarPath);
        schedules.values().forEach(repository::save);

        List<TimeSlot> blackouts = blackoutPath != null
                ? parser.parseBlackouts(blackoutPath)
                : List.of();

        var buffer = bufferMinutes == 0 ? null : Duration.ofMinutes(bufferMinutes);
        var service = new DefaultSchedulingService(repository, blackouts, buffer);
        var participants = schedules.keySet().stream().sorted().toList();

        System.out.println("Loaded " + participants.size() + " participants: " + participants);
        runInteractiveLoop(service);
        return 0;
    }

    private void runInteractiveLoop(SchedulingService service) {
        System.out.println("Type 'quit' to exit.\n");

        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Required participants (comma-separated): ");
                var requiredInput = scanner.nextLine().trim();
                if (requiredInput.equalsIgnoreCase("quit")) break;

                System.out.print("Optional participants (comma-separated, or empty): ");
                var optionalInput = scanner.nextLine().trim();
                if (optionalInput.equalsIgnoreCase("quit")) break;

                System.out.print("Duration (minutes): ");
                var durationStr = scanner.nextLine().trim();
                if (durationStr.equalsIgnoreCase("quit")) break;

                try {
                    var required = parseParticipants(requiredInput);
                    var optional = parseParticipants(optionalInput);
                    var duration = Duration.ofMinutes(Integer.parseInt(durationStr));

                    if (optional.isEmpty()) {
                        var slots = service.findAvailableSlots(required, duration);
                        printSimpleSlots(slots);
                    } else {
                        var slots = service.findAvailableSlots(required, optional, duration);
                        printDetailedSlots(slots);
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage() + "\n");
                }
            }
        }
    }

    private List<String> parseParticipants(String input) {
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void printSimpleSlots(List<TimeSlot> slots) {
        if (isEmptyWithMessage(slots)) return;
        System.out.println("Available: " + slots.stream().map(s -> s.start().toString()).toList() + "\n");
    }

    private void printDetailedSlots(List<AvailableSlot> slots) {
        if (isEmptyWithMessage(slots)) return;
        System.out.println("Available slots:");
        for (var slot : slots) {
            System.out.printf("  %s - Optional available: %s, unavailable: %s%n",
                    slot.timeSlot().start(),
                    slot.availableOptionalParticipants(),
                    slot.unavailableOptionalParticipants());
        }
        System.out.println();
    }

    private boolean isEmptyWithMessage(List<?> slots) {
        if (slots.isEmpty()) {
            System.out.println("No slots found.\n");
            return true;
        }
        return false;
    }
}
