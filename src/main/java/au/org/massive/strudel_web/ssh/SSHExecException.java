package au.org.massive.strudel_web.ssh;

import java.io.IOException;

/**
 * Thrown when an error occurs during SSH command execution
 */
public class SSHExecException extends IOException {
    SSHExecException(String message, Throwable cause) {
        super(message, cause);
    }
}
