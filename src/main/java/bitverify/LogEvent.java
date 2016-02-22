package bitverify;

import java.util.logging.Level;

/**
 * Created by Rob on 19/02/2016.
 */
public class LogEvent {
    private final String message;
    private final LogEventSource source;
    private final Level level;
    private final long timeStamp;

    public LogEvent(String message, LogEventSource source, Level level) {
        this.message = message;
        this.source = source;
        this.level = level;

        this.timeStamp = System.currentTimeMillis();
    }

    public Level getLevel() {
        return level;
    }

    public LogEventSource getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public long getTimeStamp() {
        return timeStamp;
    }
}

