package io.slotify.contract;

public class SchedulerException extends RuntimeException {

    public enum ErrorType {
        INVALID_TIME_RANGE,
        PARTICIPANT_NOT_FOUND,
        PARSE_ERROR,
        REPOSITORY_ERROR
    }

    private final ErrorType errorType;

    public SchedulerException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public SchedulerException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
