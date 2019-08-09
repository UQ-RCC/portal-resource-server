package au.org.massive.strudel_web.ssh;

import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.commons.exec.ExecuteWatchdog;

import au.org.massive.strudel_web.util.UnsupportedKeyException;

/**
 * A minimal set of methods for a SSH client
 *
 * @author jrigby
 */
public interface SSHClient {

    String exec(String remoteCommands) throws IOException, SSHExecException, UnsupportedKeyException;

    String exec(String remoteCommands, ExecuteWatchdog watchdog) throws IOException, SSHExecException, UnsupportedKeyException;
    
    AsyncCommand<String> execAsync(String remoteCommands);

    class AsyncCommand<T> {
        private final ExecuteWatchdog watchdog;
        private final Future<T> future;

        public AsyncCommand(ExecuteWatchdog watchdog, Future<T> future) {
            super();
            this.watchdog = watchdog;
            this.future = future;
        }

        public ExecuteWatchdog getWatchdog() {
            return watchdog;
        }

        public Future<T> getFuture() {
            return future;
        }
    }
}
