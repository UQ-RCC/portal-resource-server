package au.org.rcc.miscs;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.log4j.Logger;

import au.org.massive.oauth2_hpc.ssh.RSAPrivateKeyCodec;
import au.org.massive.oauth2_hpc.ssh.RSAPublicKeyCodec;

/**
 * Class that provides an abstraction from the configuration files, and sensible defaults if
 * parameters are missing
 * @author jrigby
 *
 */
public class SecuritySettings {
	
	private static final Logger log = Logger.getLogger(SecuritySettings.class.getName());
	private static SecuritySettings instance;
	private Configuration config;
	
	private SecuritySettings() {
	}
	
	// this method only has effect once
	public void readConfig(String configFile) {
		if (config == null) {
			try {
				config = new PropertiesConfiguration(configFile);
			} catch (ConfigurationException e) {
				log.warn("Could not load configuration; defaulting to system configuration.", e);
				config = new SystemConfiguration();
			}			
		}	
	}
	
	public static SecuritySettings getInstance() {
		if (instance == null) {
			instance = new SecuritySettings();
		}
		return instance;
	}
	
	public RSAPublicKey getCAPublicKey() {
		String caPublicKeyFilePath = config.getString("ssh-ca-public-key");
		try {
			return RSAPublicKeyCodec.decodeKeyFromSSHBase64Format(new File(caPublicKeyFilePath));
		} catch (InvalidKeyException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public RSAPrivateKey getCAPrivateKey() {
		String caPrivateKeyFilePath = config.getString("ssh-ca-private-key");
		String caPrivateKeyPassphrase = config.getString("ssh-ca-private-key-passphrase");
		try {
			return RSAPrivateKeyCodec.decodePEMPrivateKey(new File(caPrivateKeyFilePath), caPrivateKeyPassphrase);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public int getMaxSSHCertValidity() {
		return config.getInt("ssh-cert-max-valid-days", 1);
	}
	
	public String getTokenInfoUri() {
		return config.getString("security.oauth2.resource.token-info-uri");
	}
	
	public String getJWTKeyUri() {
		return config.getString("security.oauth2.resource.jwt.key-uri");
	}
	
	public String getClientSecret() {
		return config.getString("security.oauth2.resource.clientsecret");
	}
	
	public String getClientId() {
		return config.getString("security.oauth2.resource.clientid");
	}
	
	public String getResourceId() {
		return config.getString("security.oauth2.resource.resourceid");
	}
	
	public String getAuthorityCheck() {
		return config.getString("security.oauth2.resource.authority-check", "hasAuthority('USER')");
	}
	
}
