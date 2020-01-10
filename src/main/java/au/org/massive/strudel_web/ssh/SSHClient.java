package au.org.massive.strudel_web.ssh;

import au.org.massive.strudel_web.util.UnsupportedKeyException;
import org.apache.commons.exec.ExecuteWatchdog;

import java.io.IOException;

/**
 * A minimal set of methods for a SSH client
 */
public interface SSHClient {

    String exec(String remoteCommands) throws IOException, UnsupportedKeyException;

    String exec(String remoteCommands, ExecuteWatchdog watchdog) throws IOException, UnsupportedKeyException;

    String exec(String[] args, byte[] stdin) throws IOException, UnsupportedKeyException;

    String exec(String[] args, byte[] stdin, ExecuteWatchdog watchdog) throws IOException, UnsupportedKeyException;
}
