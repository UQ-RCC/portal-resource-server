package au.org.massive.strudel_web.ssh;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;

import au.org.rcc.controller.AsyncTasks;
import au.org.massive.strudel_web.util.UnsupportedKeyException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An abstract SSH client that provides command execution and async command execution methods
 *
 * @author jrigby
 */
public abstract class AbstractSSHClient implements SSHClient {

    private final static Logger logger = LogManager.getLogger(AbstractSSHClient.class);
    private final CertAuthInfo authInfo;
    private final String viaGateway;
    private final String remoteHost;

    public AbstractSSHClient(CertAuthInfo authInfo, String remoteHost) {
        this(authInfo, remoteHost, "localhost");
    }

    public AbstractSSHClient(CertAuthInfo authInfo, String viaGateway, String remoteHost) {
        this.authInfo = authInfo;
        this.viaGateway = viaGateway;
        this.remoteHost = remoteHost;
    }

    protected Executor getForkedProcessExecutor(ExecuteWatchdog watchdog) {
        Executor exec = new DefaultExecutor();
        if (watchdog != null) {
            exec.setWatchdog(watchdog);
        }
        return exec;
    }

    protected ExecutorService getExecutorService() {
        return AsyncTasks.getExecutorService();
    }

    protected CertAuthInfo getAuthInfo() {
        return authInfo;
    }

    protected String getViaGateway() { return viaGateway; }

    protected String getRemoteHost() {
        return remoteHost;
    }

    @Override
    public String exec(String remoteCommands) throws IOException, SSHExecException, UnsupportedKeyException {
        try {
            return exec(remoteCommands, null);
        } catch (SSHExecException e) {
        	logger.error("Error running command "+remoteCommands+" on host "+remoteHost + " via "+viaGateway);
            throw e;
        }
    }

    @Override
    public String exec(String[] args, byte[] stdin) throws IOException, SSHExecException, UnsupportedKeyException {
        try {
            return exec(args, stdin, null);
        } catch (SSHExecException e) {
            logger.error("Error running command ["+String.join(" ", args)+"] on host "+remoteHost + " via "+viaGateway);
            throw e;
        }
    }

    @Override
    public AsyncCommand<String> execAsync(final String remoteCommands) {
        final ExecuteWatchdog watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
        return new AsyncCommand<>(watchdog, getExecutorService().submit(() -> exec(remoteCommands, watchdog)));
    }
    
}
