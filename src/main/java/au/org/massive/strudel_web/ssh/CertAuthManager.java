package au.org.massive.strudel_web.ssh;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

import java.security.KeyPair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import au.org.massive.oauth2_hpc.ssh.SSHCertificateGenerator;
import au.org.massive.oauth2_hpc.ssh.SSHCertificateOptions;
import au.org.massive.oauth2_hpc.ssh.SSHCertificateGenerator.SSHCertType;
import au.org.rcc.miscs.SecuritySettings;

/**
 * This is to hide the signing and creating keypair business
 * @author hoangnguyen177
 *
 */
public class CertAuthManager {
	private static CertAuthManager instance;
	private Map<String, CertAuthInfo> certAuths;
	private static final SecuritySettings securitySettings = SecuritySettings.getInstance();
	private static final Logger logger = LogManager.getLogger(CertAuthManager.class);
	
	private CertAuthManager() {
		certAuths = new HashMap<String, CertAuthInfo>();
    }
	
	public static CertAuthManager getInstance() {
		if (instance == null) {
			instance = new CertAuthManager();
		}
		return instance;
	}
	
	public CertAuthInfo getCertAuth(String username) 
			throws InvalidKeyException, SignatureException, IOException, NoSuchAlgorithmException, NoSuchProviderException {
		CertAuthInfo certAuth = null;
		if(certAuths.containsKey(username)) {
			certAuth = certAuths.get(username);
			if(certAuth.hasExpired(securitySettings.getMaxSSHCertValidity())) {
				String cert = signSSHCert((RSAPublicKey)certAuth.getKeyPair().getPublic(), username);
				certAuth.setCertificate(cert);
				certAuths.put(username, certAuth);
			}
		}
		else {
		    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", new BouncyCastleProvider());
            kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG", "SUN"));
            KeyPair keypair = kpg.generateKeyPair();
            logger.info("private key:" + keypair.getPrivate().toString());
            logger.info("public key:" + keypair.getPublic().toString());
            String cert = signSSHCert((RSAPublicKey)keypair.getPublic(), username);
            certAuth = new CertAuthInfo(username, cert, keypair);
		}
		return certAuth;
	}
	
	/**
     * generate a ssh certificate 
     * @param publicKey
     * @param user
     * @return
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws IOException
     */
    public static String signSSHCert(RSAPublicKey publicKey, String user) 
    		throws InvalidKeyException, SignatureException, IOException 
    {
    	RSAPublicKey caPublicKey = securitySettings.getCAPublicKey();
		RSAPrivateKey caPrivateKey = securitySettings.getCAPrivateKey();
		int requestedValidity = securitySettings.getMaxSSHCertValidity();
		SSHCertificateOptions certOpts = SSHCertificateOptions.builder()
				.setDefaultOptions()
				.addPrincipal(user)
				.setKeyId(InetAddress.getLocalHost().getHostName()+"-cert_"+user)
				.setPubKey(publicKey)
				.setValidDaysFromNow(requestedValidity)
				.setType(SSHCertType.SSH_CERT_TYPE_USER)
				.build();

		String signedCertificate = SSHCertificateGenerator.generateSSHCertificate(certOpts, caPublicKey, caPrivateKey);
		logger.info("Signed a certificate for "+user+" valid for "+requestedValidity+" days.");
		return signedCertificate;
	}
	
}
