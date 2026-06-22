package cloud.dbay.agent.modules;

import cloud.dbay.agent.lakebase.LakebaseClient;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LakebaseModuleProxyController {
    private static final String API_PREFIX = "/api/v1";

    private final LakebaseClient lakebaseClient;

    public LakebaseModuleProxyController(LakebaseClient lakebaseClient) {
        this.lakebaseClient = lakebaseClient;
    }

    @RequestMapping({
            "/api/v1/lakebase-legacy-agent/**",
            "/api/v1/admin/**",
            "/api/v1/auth/**",
            "/api/v1/tenants/**",
            "/api/v1/databases/**",
            "/api/v1/lbfs/**",
            "/api/v1/extensions/**",
            "/api/v1/backups/**",
            "/api/v1/pitr/**",
            "/api/v1/import/**"
    })
    public ResponseEntity<byte[]> forward(
            HttpMethod method,
            HttpServletRequest request,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body
    ) throws IOException {
        return lakebaseClient.forward(method, upstreamPathAndQuery(request), headers, body);
    }

    private String upstreamPathAndQuery(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String path = requestUri.startsWith(API_PREFIX)
                ? requestUri.substring(API_PREFIX.length())
                : requestUri;
        String query = request.getQueryString();
        return query == null || query.isBlank() ? path : path + "?" + query;
    }
}
