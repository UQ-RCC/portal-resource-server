package au.org.massive.strudel_web.ssh;

import au.org.massive.strudel_web.util.UnsupportedKeyException;
import au.org.rcc.miscs.ResourceServerSettings;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

public class CertFiles implements Closeable {
	private final Path tempDirectory;
	private final Path privKeyFile;
	private final Path certFile;

	CertFiles(CertAuthInfo authInfo) throws IOException, UnsupportedKeyException {
		tempDirectory = Files.createTempDirectory(ResourceServerSettings.getInstance().getTempDir(), "coesra-" + authInfo.getUserName());
		privKeyFile = tempDirectory.resolve("id_rsa");
		certFile = tempDirectory.resolve("id_rsa-cert.pub");

		Set<PosixFilePermission> perms = new HashSet<>();
		perms.add(PosixFilePermission.OWNER_READ);

		Set<OpenOption> opts = new HashSet<>();
		opts.add(StandardOpenOption.CREATE);
		opts.add(StandardOpenOption.TRUNCATE_EXISTING);
		opts.add(StandardOpenOption.WRITE);

		ByteBuffer bb = ByteBuffer.wrap(authInfo.getPrivateKey().getBytes(StandardCharsets.UTF_8));

		/* NB: newByteChannel() is the only way to do this atomically. */
		try(ByteChannel c = Files.newByteChannel(privKeyFile, opts, PosixFilePermissions.asFileAttribute(perms))) {
			c.write(bb);
		}

		Files.write(certFile, authInfo.getCertificate().getBytes(StandardCharsets.UTF_8));
	}

	public Path getPrivKeyFile() {
		return privKeyFile;
	}

	@SuppressWarnings("unused")
	public Path getCertFile() {
		return certFile;
	}

	@Override
	public void close() throws IOException {
		IOException ex = new IOException("Unable to delete certificate files");

		boolean failed = false;
		try {
			Files.deleteIfExists(privKeyFile);
		} catch(IOException e) {
			ex.addSuppressed(e);
			failed = true;
		}

		try {
			Files.deleteIfExists(certFile);
		} catch(IOException e) {
			ex.addSuppressed(e);
			failed = true;
		}

		try {
			Files.deleteIfExists(tempDirectory);
		} catch(IOException e) {
			ex.addSuppressed(e);
			failed = true;
		}

		if(failed) {
			throw ex;
		}
	}
}
