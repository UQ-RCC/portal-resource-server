package au.org.massive.strudel_web.vnc;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import au.org.massive.strudel_web.ssh.CertAuthInfo;
import au.org.massive.strudel_web.ssh.CertAuthManager;
import au.org.massive.strudel_web.ssh.ForkedSSHClient;
import au.org.massive.strudel_web.ssh.Tunnel;

/**
 * Manages the lifecycle of a Guacamole session
 *
 * @author jrigby
 */
public class GuacamoleSessionManager{


    private static Map<Integer, Tunnel> sshTunnels;
    private static Map<String, Set<GuacamoleSession>> guacamoleSessions;
    private static GuacamoleSessionManager instance;
    
    private GuacamoleSessionManager() {
        sshTunnels = new HashMap<>();
        guacamoleSessions = new HashMap<String, Set<GuacamoleSession>>();
    }
	
	public static GuacamoleSessionManager getInstance() {
		if (instance == null) {
			instance = new GuacamoleSessionManager();
		}
		return instance;
	}

    /**
     * Starts a Guacamole session
     * @param desktopName name to assign to desktop
     * @param vncPassword password to access VNC session
     * @param viaGateway the remote SSH server gateway
     * @param remoteHost the target of the tunnel
     * @param remotePort the remote port of the tunnel
     * @param session current session object
     * @return a GuacamoleSession with active tunnel
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public GuacamoleSession startSession(String desktopName, String vncPassword, 
    					String viaGateway, String remoteHost, int remotePort, String username) 
    							throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException {
    	System.out.println("Start new vnc session for guacamole");
        GuacamoleSession guacSession = new GuacamoleSession();
        try {
            guacSession.setName(desktopName);
            guacSession.setPassword(vncPassword);
            guacSession.setRemoteHost(remoteHost.equals("localhost")?viaGateway:remoteHost);
            guacSession.setProtocol("vnc");
            guacSession.setRemotePort(remotePort);

            // Avoid creating duplicate guacamole tunnels
            Set<GuacamoleSession> sessions = guacamoleSessions.get(username);
            if (sessions != null && sessions.contains(guacSession)) {
                for (GuacamoleSession s : sessions) {
                    if (s.equals(guacSession)) {
                        s.setName(desktopName);
                        s.setPassword(vncPassword);
                        return s;
                    }
                }
            } else {
                guacSession.setLocalPort(startTunnel(viaGateway, remoteHost, remotePort, username));
            	if(sessions == null) {
            		sessions = new HashSet<GuacamoleSession>();
            	}
		sessions.add(guacSession);
            	guacamoleSessions.put(username, sessions);
            }
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return guacSession;
    }

    public Set<GuacamoleSession> getGuacamoleSessionsSet(String username){
    	Set<GuacamoleSession> sessions = guacamoleSessions.get(username);
    	if(sessions == null) {
    		sessions = new HashSet<GuacamoleSession>();
    		guacamoleSessions.put(username, sessions);
    	}
    	return sessions;
    }
    
    public void endSession(GuacamoleSession guacSession, String username) {
        stopTunnel(guacSession.getLocalPort());
        guacamoleSessions.get(username).remove(guacSession);
    }

    private static int startTunnel(String viaGateway, String remoteHost, int remotePort, String username) 
    		throws IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException {
    	CertAuthInfo certAuth = CertAuthManager.getInstance().getCertAuth(username);
    	ForkedSSHClient sshClient = new ForkedSSHClient(certAuth, viaGateway, remoteHost);
        Tunnel t = sshClient.startTunnel(remotePort, 0);
        sshTunnels.put(t.getLocalPort(), t);
        return t.getLocalPort();
    }

    private static boolean stopTunnel(int guacdPort) {
        if (sshTunnels.containsKey(guacdPort)) {
            Tunnel t = sshTunnels.get(guacdPort);
            if (t.isRunning()) {
                t.stopTunnel();
            }
            sshTunnels.remove(guacdPort);
            return true;
        } else {
            return false;
        }
    }
}
