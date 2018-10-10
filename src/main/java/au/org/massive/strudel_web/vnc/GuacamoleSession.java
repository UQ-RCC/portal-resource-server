package au.org.massive.strudel_web.vnc;

/**
 * Parameters requires for the Guacamole database
 *
 * @author jrigby
 */
public class GuacamoleSession {
    private static int instanceCount = 0;
    private int id;
    private String name;
    private String guacHostName;
    private int localPort;
    private String remoteHost;
    private int remotePort;
    private String protocol;
    private String password;

    public GuacamoleSession() {
        instanceCount ++;
        this.id = instanceCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int port) {
        this.localPort = port;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int port) {
        this.remotePort = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHostName() {
    	return this.guacHostName;
    }
    
    public void setHostName(String hName) {
    	this.guacHostName = hName;
    }

    
    public String toString() {
        return getProtocol()+"://"+ getRemoteHost()+":"+ getRemotePort()+"/";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GuacamoleSession) {
            return toString().equals(o.toString());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
