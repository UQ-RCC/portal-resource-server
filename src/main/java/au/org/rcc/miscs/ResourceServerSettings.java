package au.org.rcc.miscs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ResourceServerSettings {
	@Autowired
	@Value("${resource-server.remote-host}")
	private String remoteHost;

	private Path tmpDir;

	@Autowired
	private void setTmpDir(@Value("${resource-server.tmpdir}") Path tmpDir) throws IOException {
		this.tmpDir = Files.createTempDirectory(tmpDir, "resource-server-");
	}

	public Path getTmpDir() {
		return tmpDir;
	}

	public String getRemoteHost() {
		return remoteHost;
	}
}
