package au.org.massive.strudel_web.job_control;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a message that should be displayed to the user via the UI
 */
public class UserMessage {
    public enum MessageType {
        INFORMATION("info"), WARNING("warn"), ERROR("error");

        private String name;
        MessageType(String asString) {
            name = asString;
        }
        public String toString() {
            return name;
        }

        public static MessageType fromString(String type) {
            if (type != null) {
                for (MessageType t : MessageType.values()) {
                    if (t.toString().toLowerCase().equals(type.toLowerCase())) {
                        return t;
                    }
                }
            }
            return null;
        }
    }

    private long timestamp;
    private List<Long> previousOccurances;
    private MessageType type;
    private String message;
    private int count;
    public UserMessage(MessageType type, String message) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.message = message;
        this.count = 1;
        this.previousOccurances = new LinkedList<>();
    }

    public MessageType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String toString() {
        return getMessage();
    }

    /**
     * Increments the number of times this message have been encountered
     */
    public void incrementCount() {
        count ++;
        this.previousOccurances.add(this.timestamp);
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
