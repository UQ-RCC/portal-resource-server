package au.org.rcc.miscs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	private static ResourceServerSettings instance;
	private String jsonFile = "";
	private String remoteHost = "";
	private Path tempDir = Paths.get("/tmp/");

	public static ResourceServerSettings getInstance() {
		if (instance == null) {
			instance = new ResourceServerSettings();
		}
		return instance;
	}

	public void setRemoteHost(String rHost) {
		remoteHost = rHost;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public Path getTempDir() {
		return this.tempDir;
	}

	public void setTempDir(Path dir) {
		this.tempDir = dir;
	}
}
