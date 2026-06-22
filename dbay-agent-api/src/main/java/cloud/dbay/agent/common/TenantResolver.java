package cloud.dbay.agent.common;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class TenantResolver {
    private TenantResolver() {}

    public static String resolve(HttpServletRequest request) {
        String tenant = firstNonBlank(
                request.getHeader("X-DBay-Tenant-Id"),
                request.getHeader("X-Tenant-Id"),
                request.getHeader("X-Lakeon-Tenant-Id"));
        if (tenant != null) {
            return tenant;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && !auth.isBlank()) {
            if (!auth.startsWith("Bearer lk_")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
            }
            return "auth_" + sha256(auth).substring(0, 16);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
