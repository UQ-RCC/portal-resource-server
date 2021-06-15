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

import au.org.massive.oauth2_hpc.ssh.SSHCertificateGenerator;
import au.org.massive.oauth2_hpc.ssh.SSHCertificateGenerator.SSHCertType;
import au.org.massive.oauth2_hpc.ssh.SSHCertificateOptions;
import com.kstruct.gethostname4j.Hostname;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * This is to hide the signing and creating keypair business
 *
 * @author hoangnguyen177
 * @author z.vaniperen
 */
@Component
public class CertAuthManager {
	private Map<String, CertAuthInfo> certAuths;
	private static final Logger LOGGER = LogManager.getLogger(CertAuthManager.class);

	private RSAPublicKey caPublicKey;
	private RSAPrivateKey caPrivateKey;

	private Duration keyValidity;

	@Autowired
	@Value("${resource-server.cert.key-algorithm}")
	private String keyAlgorithm;

	@Autowired
	@Value("${resource-server.cert.key-bits}")
	private int keyBits;

	@Autowired
	@Value("${resource-server.cert.rng-algorithm}")
	private String rngAlgorithm;

	public CertAuthManager() {
		this.certAuths = new HashMap<>();
	}

	@Autowired
	private void setKeyValidity(@Value("${resource-server.cert.validity}") long validity) {
		keyValidity = Duration.ofSeconds(validity);
	}

	@Autowired
	private void setSshCaPrivate(@Value("${resource-server.cert.ca-private}") Path path, @Value("${resource-server.cert.ca-passphrase}") String passphrase) throws IOException {
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

	public CertAuthInfo getCertAuth(String username) throws GeneralSecurityException, IOException {
		CertAuthInfo certAuth = certAuths.get(username);
		Instant validAfter = Instant.now();
		Instant validBefore = validAfter.plus(keyValidity);

		KeyPair keyPair;
		if(certAuth != null) {
			if(!certAuth.hasExpired()) {
				return certAuth;
			}
			keyPair = certAuth.getKeyPair();
		} else {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlgorithm);
			kpg.initialize(keyBits, SecureRandom.getInstance(rngAlgorithm));
			keyPair = kpg.generateKeyPair();
		}

		SSHCertificateOptions certOpts = SSHCertificateOptions.builder()
				.setDefaultOptions()
				.addPrincipal(username)
				.setKeyId(Hostname.getHostname() + "-cert_" + username)
				.setPubKey((RSAPublicKey)keyPair.getPublic())
				.setValidAfter(validAfter.getEpochSecond())
				.setValidBefore(validBefore.getEpochSecond())
				.setType(SSHCertType.SSH_CERT_TYPE_USER)
				.build();

		String cert = SSHCertificateGenerator.generateSSHCertificate(certOpts, caPublicKey, caPrivateKey);
		LOGGER.info("Signed a certificate for {} valid until {}.", username, validBefore);

		certAuth = new CertAuthInfo(username, cert, keyPair, validAfter, validBefore);
		certAuths.put(username, certAuth);
		return certAuth;
	}
}
