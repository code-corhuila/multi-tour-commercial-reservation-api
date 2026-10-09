package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.LoggerFactory;

public class JwtPublicKeyLoader {
    public RSAPublicKey load(String inline, String file) {
        try {
            boolean hasInline = inline != null && !inline.isBlank();
            boolean hasFile = file != null && !file.isBlank();
            if (hasInline && hasFile) {
                LoggerFactory.getLogger(JwtPublicKeyLoader.class)
                        .warn("Both JWT_PUBLIC_KEY and JWT_PUBLIC_KEY_FILE are set; using JWT_PUBLIC_KEY");
            }
            String pem = hasInline ? inline.replace("\\n", "\n")
                    : hasFile ? Files.readString(Path.of(file)) : "";
            String begin = "-----BEGIN PUBLIC KEY-----";
            String end = "-----END PUBLIC KEY-----";
            pem = pem.strip();
            if (!pem.startsWith(begin) || !pem.endsWith(end)) throw new IllegalArgumentException();
            String encoded = pem.substring(begin.length(), pem.length() - end.length()).replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception failure) {
            // Parsing diagnostics can contain key material, so startup reports only configuration names.
            throw new IllegalStateException("cannot load an RSA public key from JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE");
        }
    }
}
