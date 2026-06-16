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
            "/api/v1/knowledge/**",
            "/api/v1/memory/**",
            "/api/v1/datalake/**",
            "/api/v1/agent-state/**",
            "/api/v1/pipelines/**",
            "/api/v1/pipeline-runs/**",
            "/api/v1/pipeline-components/**"
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
