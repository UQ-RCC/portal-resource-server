package au.org.massive.strudel_web.ssh;

import java.util.concurrent.TimeUnit;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import au.org.massive.strudel_web.util.KeyService;
import au.org.massive.strudel_web.util.UnsupportedKeyException;
/**
 * Contains certificate details for SSH auth
 *
 * @author jrigby
 */
public class CertAuthInfo {
    private final String userName;
    private String certificate;
    private Long createdTime;
    private final KeyPair keypair;
    
    public CertAuthInfo(String userName, String certificate, KeyPair keypair) {
        super();
        this.userName = userName;
        this.certificate = certificate;
        this.keypair = keypair;
        createdTime = System.currentTimeMillis();
    }

    public String getUserName() {
        return userName;
    }

    public String getCertificate() {
        return certificate;
    }

    public String getPrivateKey() throws UnsupportedKeyException {
        return KeyService.keyToString((RSAPrivateKey)this.keypair.getPrivate());
    }
    
    public String getPublicKey() throws UnsupportedKeyException {
    	return KeyService.keyToString((RSAPublicKey) this.keypair.getPublic());
    }
    
    public KeyPair getKeyPair() {
    	return this.keypair;
    }
    
    public Long getCreatedTime() {
        return createdTime;
    }

    public Long timeSinceCreated() {
        return System.currentTimeMillis() - getCreatedTime();
    }
    
    public boolean hasExpired(int validDays) {
        return (this.timeSinceCreated()/1000 > validDays*24*3600);    	
    }
    
    public void setCertificate(String cert) {
    	this.certificate = cert;
    	createdTime = System.currentTimeMillis();
    }

    public String formattedTimeSinceCreated() {
        Long timeSinceCreated = timeSinceCreated();
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(timeSinceCreated),
                TimeUnit.MILLISECONDS.toMinutes(timeSinceCreated) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeSinceCreated)),
                TimeUnit.MILLISECONDS.toSeconds(timeSinceCreated) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeSinceCreated)));
    }
    
    
}
