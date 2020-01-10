/*
 * RCC Portals Resource Server
 * https://github.com/UQ-RCC/portal-resource-server
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.portal.resource.ssh;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

	public CertFiles(CertAuthInfo authInfo, Path tmpDir) throws IOException {
		tempDirectory = Files.createTempDirectory(tmpDir, "coesra-" + authInfo.getUserName());
		privKeyFile = tempDirectory.resolve("id_rsa");
		certFile = tempDirectory.resolve("id_rsa-cert.pub");

		Set<PosixFilePermission> perms = new HashSet<>();
		perms.add(PosixFilePermission.OWNER_READ);

		Set<OpenOption> opts = new HashSet<>();
		opts.add(StandardOpenOption.CREATE);
		opts.add(StandardOpenOption.TRUNCATE_EXISTING);
		opts.add(StandardOpenOption.WRITE);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try(JcaPEMWriter pemw = new JcaPEMWriter(new OutputStreamWriter(baos))) {
			pemw.writeObject(authInfo.getKeyPair().getPrivate());
		}

		/* NB: newByteChannel() is the only way to do this atomically. */
		try(ByteChannel c = Files.newByteChannel(privKeyFile, opts, PosixFilePermissions.asFileAttribute(perms))) {
			c.write(ByteBuffer.wrap(baos.toByteArray()));
		}

		Files.write(certFile, authInfo.getCertificate().getBytes(StandardCharsets.UTF_8));
	}

	public Path getPrivKeyFile() {
		return privKeyFile;
	}

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
