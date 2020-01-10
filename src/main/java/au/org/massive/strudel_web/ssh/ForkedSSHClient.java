package au.org.massive.strudel_web.ssh;

import au.org.massive.strudel_web.util.UnsupportedKeyException;
import au.edu.uq.rcc.portal.resource.ssh.CertAuthInfo;
import au.edu.uq.rcc.portal.resource.ssh.CertFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A non-native SSH client implmentation that forks SSH processes for each request.
 * Depends on an SSH binary in the search path. Certificates are written to disk at the beginning of each command
 * and deleted upon command completion.
 *
 * @author jrigby
 */
public class ForkedSSHClient extends AbstractSSHClient {

    private final static Logger logger = LogManager.getLogger(ForkedSSHClient.class);
    private final CertAuthInfo authInfo;
    private final Path tmpDir;

    public ForkedSSHClient(CertAuthInfo authInfo, String remoteHost, Path tmpDir) {
        super(authInfo, remoteHost);
        this.authInfo = authInfo;
        this.tmpDir = tmpDir;
    }

    public ForkedSSHClient(CertAuthInfo authInfo, String viaGateway, String remoteHost, Path tmpDir) {
        super(authInfo, viaGateway, remoteHost);
        this.authInfo = authInfo;
        this.tmpDir = tmpDir;
    }

    private int findFreePort() throws IOException {
        ServerSocket s = new ServerSocket(0);
        int freePort = s.getLocalPort();
        s.close();
        return freePort;
    }

    /**
     * Starts an SSH tunnel
     *
     * @param remotePort the port to forward
     * @param maxUptimeInSeconds the maximum length of time for the tunnel to remain open. Zero represents infinity.
     * @return a {@link Tunnel} object
     * @throws IOException thrown on errors reading data streams
     */
    public Tunnel startTunnel(final int remotePort, int maxUptimeInSeconds) throws IOException {
        final int localPort = findFreePort();
        final ExecuteWatchdog watchdog;
        if (maxUptimeInSeconds < 1) {
            watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
        } else {
            watchdog = new ExecuteWatchdog((long) maxUptimeInSeconds * 1000);
        }
        getExecutorService().submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                Map<String, String> tunnelFlags = new HashMap<>();
                tunnelFlags.put("-L" + localPort + ":"+getRemoteHost()+":" + remotePort, "");
                return exec("sleep infinity", tunnelFlags, watchdog);
            }

        });

        return new Tunnel() {

            @Override
            public int getLocalPort() {
                return localPort;
            }

            @Override
            public int getRemotePort() {
                return remotePort;
            }

            @Override
            public String getRemoteHost() {
                return ForkedSSHClient.this.getRemoteHost();
            }

            @Override
            public void stopTunnel() {
                watchdog.destroyProcess();
            }

            @Override
            public boolean isRunning() {
                return watchdog.isWatching();
            }

        };
    }

    /**
     * Executes a remote command
     *
     * @param remoteCommands commands to execute
     * @param watchdog       can be used to kill runaway processes
     * @return the command results
     * @throws UnsupportedKeyException 
     */
    @Override
    public String exec(String remoteCommands, ExecuteWatchdog watchdog) throws IOException, SSHExecException, UnsupportedKeyException {
        return exec(remoteCommands, null, watchdog);
    }

    @Override
    public String exec(String[] args, byte[] stdin, ExecuteWatchdog watchdog) throws IOException, SSHExecException, UnsupportedKeyException {
        return exec(args, stdin, null, watchdog);
    }

    /**
     * Exeucte a remote command
     *
     * @param remoteCommands commands to execute
     * @param extraFlags     a map of flags and arguments for the SSH process; map values are allowed to be empty or null
     * @param watchdog can be used to kill runaway processes
     * @return the command results
     * @throws IOException thrown on errors reading data streams
     * @throws SSHExecException thrown on errors caused during SSH command execution
     * @throws UnsupportedKeyException 
     */
    public String exec(String remoteCommands, Map<String, String> extraFlags, ExecuteWatchdog watchdog)
    		throws IOException, SSHExecException, UnsupportedKeyException {
        return exec(new String[]{"bash", "-s"}, remoteCommands.getBytes(StandardCharsets.UTF_8), extraFlags, watchdog);
    }

    public String exec(String[] args, byte[] stdin, Map<String, String> extraFlags, ExecuteWatchdog watchdog)
            throws IOException, SSHExecException, UnsupportedKeyException {

        CertFiles certFiles = new CertFiles(authInfo, tmpDir);

        /* Socket path, needs to be reproducible. */
        String sshConnection = "ssh://" + getAuthInfo().getUserName() + "@" + getViaGateway();
        Path socketPath = tmpDir.resolve(String.format("sshsock-%d", sshConnection.hashCode()));

        CommandLine cmdLine = new CommandLine("ssh");
        cmdLine.addArgument("-q");
        cmdLine.addArgument("-i");
        cmdLine.addArgument(certFiles.getPrivKeyFile().toAbsolutePath().toString());
        cmdLine.addArgument(String.format("-oCertificateFile=%s", certFiles.getCertFile().toAbsolutePath().toString()));
        cmdLine.addArgument("-oUserKnownHostsFile=/dev/null");
        cmdLine.addArgument("-oStrictHostKeyChecking=no"); // TODO: Remove me when ready
        cmdLine.addArgument("-oBatchMode=yes");
        cmdLine.addArgument("-oKbdInteractiveAuthentication=no");
        cmdLine.addArgument("-oControlMaster=auto");
        cmdLine.addArgument("-oControlPersist=15m");
        cmdLine.addArgument(String.format("-oControlPath=%s", socketPath));
        cmdLine.addArgument("-l");
        cmdLine.addArgument(getAuthInfo().getUserName());
        cmdLine.addArgument(getViaGateway());

        // Add extra flags
        // TODO: These should be validated as OpenSSH flags only
        if (extraFlags != null && extraFlags.size() > 0) {
            for (String key : extraFlags.keySet()) {
                String value = extraFlags.get(key);
                cmdLine.addArgument(key);
                if (value != null && value.length() > 0) {
                    cmdLine.addArgument(value);
                }
            }
        }
        cmdLine.addArgument("--");
        cmdLine.addArguments(args);

        Gson gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                //.setPrettyPrinting()
                .create();

        Map<String, Object> logMap = new HashMap<>();
        logMap.put("timestamp", Instant.now().toString());
        logMap.put("type", "ssh");
        logMap.put("user", getAuthInfo().getUserName());
        logMap.put("command", cmdLine.getArguments());
        logMap.put("input", new String(stdin, StandardCharsets.UTF_8));

        ByteArrayInputStream input = new ByteArrayInputStream(stdin);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Executor exec = getForkedProcessExecutor(watchdog);
        exec.setStreamHandler(new PumpStreamHandler(output, output, input));
        try {
            exec.execute(cmdLine);
        }
        catch (ExecuteException e) {
            logger.error("SSH command failed: "+cmdLine.toString()+"\n"+
                    "Remote commands: ["+String.join(", ", args)+"]\n"+
                    "Remote server said: " + output.toString());
            throw new SSHExecException(output.toString(), e);
        } finally {
            logMap.put("output", output.toString());
            logger.info("AUDIT: {}", gson.toJson(logMap));
            certFiles.close();
        }
        return output.toString();
    }
}
