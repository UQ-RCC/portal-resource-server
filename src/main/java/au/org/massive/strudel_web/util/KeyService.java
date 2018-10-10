package au.org.massive.strudel_web.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;


public class KeyService {
	/**
	 * Converts a key that's subclassed from {@link RSAKey} to a string representation
	 *
	 * @param key a key extending RSAKey
	 * @param <E> the type of key to convert to a string
	 * @return String representation of the key
	 * @throws UnsupportedKeyException thrown if the key is neither an {@link RSAPrivateKey} or {@link RSAPublicKey}
	 */
	public static <E extends RSAKey> String keyToString(E key) throws UnsupportedKeyException {
        String header = "", footer = "", keyString = "";
        if (key instanceof RSAPrivateKey) {
            // Return the private key in PEM format
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            OutputStreamWriter out = new OutputStreamWriter(buf);
            JcaPEMWriter pemWriter = new JcaPEMWriter(out);
            try {
                pemWriter.writeObject(key);
                pemWriter.close();
                out.close();
                keyString = new String(buf.toByteArray());
                buf.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (key instanceof RSAPublicKey) {
            // Return the public key in SSH format (suitable for authorized_keys)
            header = "ssh-rsa ";
            footer = " jobcontrol@" + System.currentTimeMillis();
            byte[] keyBytes;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            RSAPublicKey rsaPubKey = (RSAPublicKey) key;
            DataOutputStream dos = new DataOutputStream(buf);
            try {
                dos.write(new byte[]{0, 0, 0, 7, 's', 's', 'h', '-', 'r', 's', 'a'});
                dos.writeInt(rsaPubKey.getPublicExponent().toByteArray().length);
                dos.write(rsaPubKey.getPublicExponent().toByteArray());
                dos.writeInt(rsaPubKey.getModulus().toByteArray().length);
                dos.write(rsaPubKey.getModulus().toByteArray());
                dos.flush();
                buf.flush();
                keyBytes = buf.toByteArray();
                dos.close();
                buf.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            keyString = new String(Base64.encodeBase64(keyBytes));
        } else {
            throw new UnsupportedKeyException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header);
        sb.append(keyString);
        sb.append(footer);

        return sb.toString();
    }

}
