package au.org.massive.strudel_web.ssh;

/**
 * Thrown when an error occurs during SSH command execution
 *
 * @author jrigby
 */
public class SSHExecException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -8166014793755298357L;

    public SSHExecException() {
        super();
    }

    public SSHExecException(String message, Throwable cause,
                            boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public SSHExecException(String message, Throwable cause) {
        super(message, cause);
    }

    public SSHExecException(String message) {
        super(message);
    }

    public SSHExecException(Throwable cause) {
        super(cause);
    }

}
