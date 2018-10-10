package au.org.massive.strudel_web.job_control;

/**
 * Thrown if a job is attempted without the required parameters
 *
 * @author jrigby
 */
public class MissingRequiredTaskParametersException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -8194220157009834131L;

    public MissingRequiredTaskParametersException() {
        super();
    }

    public MissingRequiredTaskParametersException(String message,
                                                  Throwable cause, boolean enableSuppression,
                                                  boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MissingRequiredTaskParametersException(String message, Throwable cause) {
        super(message, cause);
    }

    public MissingRequiredTaskParametersException(String message) {
        super(message);
    }

    public MissingRequiredTaskParametersException(Throwable cause) {
        super(cause);
    }

}
