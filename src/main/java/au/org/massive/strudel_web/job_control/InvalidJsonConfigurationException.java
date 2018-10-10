package au.org.massive.strudel_web.job_control;

/**
 * Created by jason on 21/09/15.
 */
public class InvalidJsonConfigurationException extends Exception {
    public InvalidJsonConfigurationException() {
    }

    public InvalidJsonConfigurationException(String message) {
        super(message);
    }

    public InvalidJsonConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidJsonConfigurationException(Throwable cause) {
        super(cause);
    }

    public InvalidJsonConfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
