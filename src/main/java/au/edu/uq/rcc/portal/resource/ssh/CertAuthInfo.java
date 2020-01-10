package au.edu.uq.rcc.portal.resource.ssh;

import java.security.KeyPair;
import java.time.Instant;

/**
 * Contains certificate details for SSH auth
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class CertAuthInfo {
	private final String userName;
	private String certificate;
	private final KeyPair keypair;
	private Instant validAfter;
	private Instant validBefore;

	public CertAuthInfo(String userName, String certificate, KeyPair keypair, Instant validAfter, Instant validBefore) {
		super();
		this.userName = userName;
		this.certificate = certificate;
		this.keypair = keypair;
		this.validAfter = validAfter;
		this.validBefore = validBefore;
	}

	public String getUserName() {
		return userName;
	}

	public String getCertificate() {
		return this.certificate;
	}

	public KeyPair getKeyPair() {
		return this.keypair;
	}

	public boolean hasExpired() {
		return Instant.now().isAfter(validBefore);
	}

	public Instant getValidAfter() {
		return this.validAfter;
	}

	public Instant getValidBefore() {
		return this.validBefore;
	}

}
