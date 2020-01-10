package au.org.massive.strudel_web.ssh;

/**
 * Methods to control an SSH tunnel
 */
public interface Tunnel {
    int getLocalPort();

    int getRemotePort();

    String getRemoteHost();

    void stopTunnel();

    boolean isRunning();
}
