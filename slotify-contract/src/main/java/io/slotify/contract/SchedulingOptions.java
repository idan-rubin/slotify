package io.slotify.contract;

import java.time.Duration;

public record SchedulingOptions(Duration bufferBetweenMeetings) {

    public static SchedulingOptions defaults() {
        return new SchedulingOptions(null);
    }

    public static SchedulingOptions withBuffer(Duration buffer) {
        if (buffer == null || buffer.isNegative()) {
            throw new SchedulerException(SchedulerException.ErrorType.INVALID_TIME_RANGE,
                    "Buffer must be non-null and non-negative");
        }
        return new SchedulingOptions(buffer);
    }

    public boolean hasBuffer() {
        return bufferBetweenMeetings != null && !bufferBetweenMeetings.isZero();
    }
}
