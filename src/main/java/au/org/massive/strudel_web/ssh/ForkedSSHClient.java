package au.org.massive.strudel_web.ssh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import au.org.massive.strudel_web.util.UnsupportedKeyException;
import au.org.rcc.miscs.ResourceServerSettings;
import org.codehaus.jackson.annotate.JsonManagedReference;

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

    public ForkedSSHClient(CertAuthInfo authInfo, String remoteHost) {
        super(authInfo, remoteHost);
        this.authInfo = authInfo;
    }

    public ForkedSSHClient(CertAuthInfo authInfo, String viaGateway, String remoteHost) {
        super(authInfo, viaGateway, remoteHost);
        this.authInfo = authInfo;
    }

    private class CertFiles implements Closeable {
        private final File tempDirectory;
        private final File privKeyFile;
        private final File certFile;
        private final File tempPrivKeyFile;
        
        public CertFiles(CertAuthInfo authInfo) throws IOException, UnsupportedKeyException {
            super();
            Path defaultPath = FileSystems.getDefault().getPath(ResourceServerSettings.getInstance().getTempDir());
            Path tempDirectoryPath = 
            Files.createTempDirectory(defaultPath, "coesra-" + authInfo.getUserName());
            privKeyFile = tempDirectoryPath.resolve("id_rsa").toFile();
            privKeyFile.createNewFile();
            
            certFile = tempDirectoryPath.resolve("id_rsa-cert.pub").toFile();
            tempDirectory = tempDirectoryPath.toFile();
            certFile.createNewFile();

            PrintWriter out = new PrintWriter(privKeyFile);
            out.println(authInfo.getPrivateKey());
            out.close();
            
            //create a new temporary file for later version of ssh
            tempPrivKeyFile = tempDirectoryPath.resolve("id_rsa-cert").toFile();
            out = new PrintWriter(tempPrivKeyFile);
            out.println(authInfo.getPrivateKey());
            out.close();

            out = new PrintWriter(certFile);
            out.println(authInfo.getCertificate());
            out.close();

            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            Files.setPosixFilePermissions(privKeyFile.toPath(), perms);
            Files.setPosixFilePermissions(tempPrivKeyFile.toPath(), perms);
        }

        public File getPrivKeyFile() {
            return privKeyFile;
        }

        @SuppressWarnings("unused")
        public File getCertFile() {
            return certFile;
        }

        @Override
        public void close() throws IOException {
            tempPrivKeyFile.delete();
            privKeyFile.delete();
            certFile.delete();
            tempDirectory.delete();
        }
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

    private static String[] disgustingCustomShellHack() {
		List<String> args = new ArrayList<>();

		for(int i = 0;; ++i) {
			String arg = System.getProperty(String.format("portal.ssh_shell.%d", i));
			if(arg == null) {
				break;
			}

			args.add(arg);
		}

		if(args.isEmpty()) {
			args.add("bash");
			args.add("-s");
			args.add("--");
		}
		return args.stream().toArray(String[]::new);
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
        CertFiles certFiles = new CertFiles(authInfo);

        CommandLine cmdLine = new CommandLine("ssh");
        //cmdLine.addArgument("-q");
        cmdLine.addArgument("-q");
        cmdLine.addArgument("-i");
        cmdLine.addArgument(certFiles.getPrivKeyFile().getAbsolutePath());
        cmdLine.addArgument("-oUserKnownHostsFile=/dev/null");
        cmdLine.addArgument("-oStrictHostKeyChecking=no"); // TODO: Remove me when ready
        cmdLine.addArgument("-oBatchMode=yes");
        cmdLine.addArgument("-oKbdInteractiveAuthentication=no");
        cmdLine.addArgument("-oControlMaster=auto");
        cmdLine.addArgument("-oControlPersist=15m");
        String sshConnection = "ssh://" + getAuthInfo().getUserName() + "@" + getViaGateway();
        cmdLine.addArgument("-oControlPath=/run/resource-server-" + sshConnection.hashCode());
        cmdLine.addArgument("-l");
        cmdLine.addArgument(getAuthInfo().getUserName());
        cmdLine.addArgument(getViaGateway());

        // Add extra flags
        if (extraFlags != null && extraFlags.size() > 0) {
            for (String key : extraFlags.keySet()) {
                String value = extraFlags.get(key);
                cmdLine.addArgument(key);
                if (value != null && value.length() > 0) {
                    cmdLine.addArgument(value);
                }
            }
        }

        boolean hasCommands = remoteCommands != null && remoteCommands.length() > 0;
        if (hasCommands) {
			cmdLine.addArguments(disgustingCustomShellHack());
        } else {
            remoteCommands = "";
        }

        Gson gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                //.setPrettyPrinting()
                .create();

        Map<String, Object> logMap = new HashMap<>();
        logMap.put("timestamp", Instant.now().toString());
        logMap.put("type", "ssh");
        logMap.put("user", getAuthInfo().getUserName());
        logMap.put("command", cmdLine.getArguments());
        logMap.put("input", remoteCommands);

        ByteArrayInputStream input = new ByteArrayInputStream(remoteCommands.getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Executor exec = getForkedProcessExecutor(watchdog);
        exec.setStreamHandler(new PumpStreamHandler(output, output, input));
        try {
            exec.execute(cmdLine);
        }
        catch (ExecuteException e) {
            logger.error("SSH command failed: "+cmdLine.toString()+"\n"+
                         "Remote commands: "+remoteCommands+"\n"+
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
