package au.org.massive.strudel_web.vnc;

import au.edu.uq.rcc.portal.resource.ssh.CertAuthInfo;
import au.edu.uq.rcc.portal.resource.ssh.CertAuthManager;
import au.org.massive.strudel_web.ssh.ForkedSSHClient;
import au.org.massive.strudel_web.ssh.Tunnel;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Manages the lifecycle of a Guacamole session
 *
 * @author jrigby
 */
public class GuacamoleSessionManager {
	private final Path tmpDir;
	private final CertAuthManager certAuthManager;
	private final ExecutorService executorService;
	private final Map<Integer, Tunnel> sshTunnels;
	private final Map<String, Set<GuacamoleSession>> guacamoleSessions;


	public GuacamoleSessionManager(Path tmpDir, CertAuthManager certAuthManager, ExecutorService executorService) {
		this.tmpDir = tmpDir;
		this.certAuthManager = certAuthManager;
		this.executorService = executorService;
		this.sshTunnels = new HashMap<>();
		this.guacamoleSessions = new HashMap<>();
	}

	/**
	 * Starts a Guacamole session
	 *
	 * @param desktopName name to assign to desktop
	 * @param vncPassword password to access VNC session
	 * @param viaGateway  the remote SSH server gateway
	 * @param remoteHost  the target of the tunnel
	 * @param remotePort  the remote port of the tunnel
	 * @return a GuacamoleSession with active tunnel
	 */
	public GuacamoleSession startSession(String desktopName, String vncPassword,
										 String viaGateway, String remoteHost, int remotePort, String username)
			throws GeneralSecurityException {
		System.out.println("Start new vnc session for guacamole");
		GuacamoleSession guacSession = new GuacamoleSession();
		try {
			guacSession.setName(desktopName);
			guacSession.setPassword(vncPassword);
			guacSession.setRemoteHost(remoteHost.equals("localhost") ? viaGateway : remoteHost);
			guacSession.setProtocol("vnc");
			guacSession.setRemotePort(remotePort);

			// Avoid creating duplicate guacamole tunnels
			Set<GuacamoleSession> sessions = guacamoleSessions.get(username);
			if(sessions != null && sessions.contains(guacSession)) {
				for(GuacamoleSession s : sessions) {
					if(s.equals(guacSession)) {
						s.setName(desktopName);
						s.setPassword(vncPassword);
						return s;
					}
				}
			} else {
				guacSession.setLocalPort(startTunnel(viaGateway, remoteHost, remotePort, username));
				if(sessions == null) {
					sessions = new HashSet<>();
				}
				sessions.add(guacSession);
				guacamoleSessions.put(username, sessions);
			}

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
		return guacSession;
	}

	public Set<GuacamoleSession> getGuacamoleSessionsSet(String username) {
		return guacamoleSessions.computeIfAbsent(username, k -> new HashSet<>());
	}

	public void endSession(GuacamoleSession guacSession, String username) {
		stopTunnel(guacSession.getLocalPort());
		guacamoleSessions.get(username).remove(guacSession);
	}

	private int startTunnel(String viaGateway, String remoteHost, int remotePort, String username)
			throws IOException, GeneralSecurityException {
		CertAuthInfo certAuth = certAuthManager.getCertAuth(username);
		ForkedSSHClient sshClient = new ForkedSSHClient(certAuth, viaGateway, remoteHost, tmpDir);
		Tunnel t = sshClient.startTunnel(remotePort, 0, executorService);
		sshTunnels.put(t.getLocalPort(), t);
		return t.getLocalPort();
	}

	private void stopTunnel(int guacdPort) {
		if(!sshTunnels.containsKey(guacdPort)) {
			return;
		}

		Tunnel t = sshTunnels.get(guacdPort);
		if(t.isRunning()) {
			t.stopTunnel();
		}
		sshTunnels.remove(guacdPort);
	}
}
