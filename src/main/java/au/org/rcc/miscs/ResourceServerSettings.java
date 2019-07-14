package au.org.rcc.miscs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import au.org.massive.strudel_web.job_control.AbstractSystemConfiguration;
import au.org.massive.strudel_web.job_control.InvalidJsonConfigurationException;
import au.org.massive.strudel_web.job_control.StrudelDesktopConfigurationAdapter;
import au.org.massive.strudel_web.job_control.ConfigurationRegistry;
/**
 * Config for the app
 * @author hoangnguyen177
 *
 */
public class ResourceServerSettings {
	private ConfigurationRegistry CONFIGURATION_REGISTRY;
	private static ResourceServerSettings instance;
	private String jsonFile = "";
	private String resourceServerHost ="";
	private int resourceServerPort = 8000;
	private String resourceServerProtocol = "";
	private String remoteHost = "";
	private String tempDir = "/tmp/";
	private String rootContext  = "/";
	
	private ResourceServerSettings() {
		CONFIGURATION_REGISTRY = new ConfigurationRegistry();
	}
	
	public static ResourceServerSettings getInstance() {
		if (instance == null) {
			instance = new ResourceServerSettings();
		}
		return instance;
	}
	
	public void setJsonConfigFile(String file) throws IOException, InvalidJsonConfigurationException, Exception {
		if(!jsonFile.trim().isEmpty())
			throw new Exception("JSON config file was already set");
		this.jsonFile = file;
		this.setupSystemConfig();
	}
	
	/**
	 * populate CONFIGURATION_REGISTRY
	 * @throws IOException 
	 * @throws InvalidJsonConfigurationException 
	 */
	private void setupSystemConfig() throws IOException, InvalidJsonConfigurationException {
		BufferedReader in = new BufferedReader(
                new FileReader(this.jsonFile));
		StringBuilder jsonFile = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            jsonFile.append(inputLine);
        }
        in.close();
        StrudelDesktopConfigurationAdapter strudelConfig = new StrudelDesktopConfigurationAdapter("", jsonFile.toString());
        for (String configId : strudelConfig.keySet()) {
            AbstractSystemConfiguration c = strudelConfig.get(configId);
            CONFIGURATION_REGISTRY.addSystemConfiguration(configId, c);
        }
	}
	
	public void setResourceServerPort(int p) {
		resourceServerPort = p;
	}
	
	public String getResourceServerHost() {
		return resourceServerHost;
	}
	
	public int getResourceServerPort() {
		return resourceServerPort;
	}
	
	public String getResourceServerProtocol() {
		return resourceServerProtocol;
	}
	
	public void setResourceServerProtocol(String p) {
		resourceServerProtocol = p;
	}
		
	public ConfigurationRegistry getSystemConfigurations() {
        return CONFIGURATION_REGISTRY;
    }
	
	
	public void setRemoteHost(String rHost) {
		remoteHost = rHost;
	}
	
	public void setResourceServerHost(String host) {
		resourceServerHost = host;
	}
	
	public String getRemoteHost() {
		return remoteHost;
	}
	
	public String getTempDir() {
		return this.tempDir;
	}
	
	public void setTempDir(String dir) {
		this.tempDir = dir;
	}
	
	public String getRootContext() {
		return this.rootContext;
	}
	
	public void setRootContext(String rContext) {
		this.rootContext = rContext;
	}
}
