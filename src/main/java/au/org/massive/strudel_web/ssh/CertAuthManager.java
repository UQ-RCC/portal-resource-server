package au.org.massive.strudel_web.ssh;

import au.org.massive.oauth2_hpc.ssh.SSHCertificateGenerator;
import au.org.massive.oauth2_hpc.ssh.SSHCertificateGenerator.SSHCertType;
import au.org.massive.oauth2_hpc.ssh.SSHCertificateOptions;
import au.org.rcc.miscs.ResourceServerSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * This is to hide the signing and creating keypair business
 * @author hoangnguyen177
 *
 */

public class CertAuthManager {
	private Map<String, CertAuthInfo> certAuths;
	private static final Logger LOGGER = LogManager.getLogger(CertAuthManager.class);
	private final ResourceServerSettings settings;

	public CertAuthManager(ResourceServerSettings settings) {
		this.certAuths = new HashMap<>();
		this.settings = settings;
    }

	public CertAuthInfo getCertAuth(String username) throws GeneralSecurityException, IOException {
		CertAuthInfo certAuth = certAuths.get(username);
		Instant validAfter = Instant.now();
		Instant validBefore = validAfter.plus(settings.getKeyValidity());
		RSAPublicKey caPub = settings.getCAPublicKey();
		RSAPrivateKey caPriv = settings.getCAPrivateKey();

		KeyPair keyPair;
		if(certAuth != null) {
			if(!certAuth.hasExpired()) {
				return certAuth;
			}
			keyPair = certAuth.getKeyPair();
		} else {
		    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", new BouncyCastleProvider());
            kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG", "SUN"));
            keyPair = kpg.generateKeyPair();
		}

		SSHCertificateOptions certOpts = SSHCertificateOptions.builder()
				.setDefaultOptions()
				.addPrincipal(username)
				.setKeyId(InetAddress.getLocalHost().getHostName()+"-cert_"+username)
				.setPubKey((RSAPublicKey)keyPair.getPublic())
				.setValidAfter(validAfter.getEpochSecond())
				.setValidBefore(validBefore.getEpochSecond())
				.setType(SSHCertType.SSH_CERT_TYPE_USER)
				.build();

		String cert = SSHCertificateGenerator.generateSSHCertificate(certOpts, caPub, caPriv);
		LOGGER.info("Signed a certificate for {} valid until {}.", username, validBefore);

		certAuth = new CertAuthInfo(username, cert, keyPair, validAfter, validBefore);
		certAuths.put(username, certAuth);
		return certAuth;
	}
}
