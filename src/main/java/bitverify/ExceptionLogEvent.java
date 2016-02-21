package bitverify;

import java.util.logging.Level;

/**
 * An event to be logged that indicates an exception occurred. If this is an unhandled exception, the level should be SEVERE.
 */
public class ExceptionLogEvent extends LogEvent {
    private final Throwable cause;

    public ExceptionLogEvent(String message, LogEventSource source, Level level, Throwable cause) {
        super(message, source, level);
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}

