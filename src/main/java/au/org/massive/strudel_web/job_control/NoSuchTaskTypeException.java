package au.org.massive.strudel_web.job_control;

/**
 * Thrown if the job requested doesn't exist
 *
 * @author jrigby
 */
public class NoSuchTaskTypeException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 7254797047028370205L;

    public NoSuchTaskTypeException() {
        super();
    }

    public NoSuchTaskTypeException(String message, Throwable cause,
                                   boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public NoSuchTaskTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchTaskTypeException(String message) {
        super(message);
    }

    public NoSuchTaskTypeException(Throwable cause) {
        super(cause);
    }

}
