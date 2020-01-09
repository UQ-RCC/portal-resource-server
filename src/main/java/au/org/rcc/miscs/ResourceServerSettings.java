package au.org.rcc.miscs;

import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.bc.BcPEMDecryptorProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

@Component
public class ResourceServerSettings {
	@Autowired
	@Value("${resource-server.ssh.host}")
	private String remoteHost;

	private Path tmpDir;

	private RSAPublicKey caPublicKey;
	private RSAPrivateKey caPrivateKey;

	private Duration keyValidity;

	@Autowired
	private void setTmpDir(@Value("${resource-server.tmpdir}") Path tmpDir) throws IOException {
		this.tmpDir = Files.createTempDirectory(tmpDir, "resource-server-");
	}

	@Autowired
	private void setKeyValidity(@Value("${resource-server.ssh.validity}") long validity) {
		keyValidity = Duration.ofSeconds(validity);
	}

	@Autowired
	private void setSshCaPrivate(@Value("${resource-server.ssh.ca_private}") Path path, @Value("${resource-server.ssh.ca_passphrase}") String passphrase) throws IOException {
		Object pem;
		try(PEMParser r = new PEMParser(Files.newBufferedReader(path))) {
			pem = r.readObject();
		}

		PEMKeyPair keyPair;
		if(pem instanceof PEMKeyPair) {
			keyPair = (PEMKeyPair)pem;
		} else if(pem instanceof PEMEncryptedKeyPair) {
			keyPair = ((PEMEncryptedKeyPair)pem).decryptKeyPair(new BcPEMDecryptorProvider(passphrase.toCharArray()));
		} else {
			throw new IllegalArgumentException("Invalid CA private key");
		}

		KeyPair kp = new JcaPEMKeyConverter().getKeyPair(keyPair);

		/* FIXME: I don't like this, but until the signing code is redone... */
		PublicKey pubkey = kp.getPublic();
		PrivateKey privkey = kp.getPrivate();
		if(!(pubkey instanceof RSAPublicKey) || !(privkey instanceof RSAPrivateKey)) {
			throw new IllegalArgumentException("CA key must be RSA");
		}

		caPublicKey = (RSAPublicKey)pubkey;
		caPrivateKey = (RSAPrivateKey)privkey;

	}

	public Path getTmpDir() {
		return tmpDir;
	}

	public RSAPublicKey getCAPublicKey() {
		return caPublicKey;
	}

	public RSAPrivateKey getCAPrivateKey() {
		return caPrivateKey;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public Duration getKeyValidity() {
		return keyValidity;
	}
}
