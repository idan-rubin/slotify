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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(name = "slotify", mixinStandardHelpOptions = true, version = "1.0",
         description = "Find the perfect meeting slot for everyone")
public class App implements Callable<Integer> {

    private static final int MIN_REQUIRED_PARTICIPANTS = 2;

    @Parameters(description = "Calendar CSV file", arity = "0..1")
    private Path calendarPath;

    @Option(names = {"-b", "--blackout"}, description = "Blackout periods CSV file")
    private Path blackoutPath;

    @Option(names = {"--buffer"}, description = "Buffer minutes between meetings", defaultValue = "0")
    private int bufferMinutes;

    public static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args));
    }

    @Override
    public Integer call() {
        var parser = new CsvCalendarParser();
        var repository = new InMemoryScheduleRepository();

        try (var scanner = new Scanner(System.in)) {
            if (calendarPath == null) {
                System.out.print("Calendar CSV file path: ");
                calendarPath = Path.of(scanner.nextLine().trim());
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
            runInteractiveLoop(service, scanner);
        }
        return 0;
    }

    private void runInteractiveLoop(SchedulingService service, Scanner scanner) {
        System.out.println("Type 'quit' to exit.\n");

        while (scanner.hasNextLine()) {
            System.out.print("Required participants (comma-separated): ");
            var requiredInput = scanner.nextLine().trim();
            if (requiredInput.equalsIgnoreCase("quit")) break;

            System.out.print("Optional participants (comma-separated, or empty): ");
            if (!scanner.hasNextLine()) break;
            var optionalInput = scanner.nextLine().trim();
            if (optionalInput.equalsIgnoreCase("quit")) break;

            System.out.print("Duration (minutes): ");
            if (!scanner.hasNextLine()) break;
            var durationStr = scanner.nextLine().trim();
            if (durationStr.equalsIgnoreCase("quit")) break;

            try {
                var required = parseParticipants(requiredInput);
                if (required.size() < MIN_REQUIRED_PARTICIPANTS) {
                    System.out.printf("Error: At least %d required participants are needed%n%n", MIN_REQUIRED_PARTICIPANTS);
                    continue;
                }
                var optional = parseParticipants(optionalInput);
                var duration = Duration.ofMinutes(Integer.parseInt(durationStr));

                var slots = service.findAvailableSlots(required, optional, duration);
                printSlots(slots, optional);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage() + "\n");
            }
        }
    }

    private List<String> parseParticipants(String input) {
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void printSlots(List<AvailableSlot> slots, List<String> optional) {
        if (slots.isEmpty()) {
            System.out.println("No slots found.\n");
            return;
        }
        if (optional.isEmpty()) {
            var times = slots.stream().map(s -> s.timeSlot().start().toString()).toList();
            System.out.println("Available: " + times + "\n");
        } else {
            System.out.println("Available slots:");
            for (var slot : slots) {
                System.out.printf("  %s - Optional: available %s, unavailable %s%n",
                        slot.timeSlot().start(),
                        slot.availableOptionalParticipants(),
                        slot.unavailableOptionalParticipants());
            }
            System.out.println();
        }
    }
}
