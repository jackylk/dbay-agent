package cloud.dbay.agent.dataagent;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class TenantKeyResolver {
    public String resolve(HttpServletRequest request) {
        String explicitTenant = firstPresent(
                request.getHeader("X-DBay-Tenant-Id"),
                request.getHeader("X-Tenant-Id")
        );
        if (explicitTenant != null) {
            return explicitTenant;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            return "auth_" + sha256(authorization.trim()).substring(0, 32);
        }
        return "anonymous";
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
