package cloud.dbay.agent.modules;

import cloud.dbay.agent.lakebase.LakebaseClient;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthProxyController {
    private final LakebaseClient lakebaseClient;

    public AuthProxyController(LakebaseClient lakebaseClient) {
        this.lakebaseClient = lakebaseClient;
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<byte[]> login(HttpServletRequest request,
                                        @RequestHeader HttpHeaders headers,
                                        @RequestBody(required = false) byte[] body) throws IOException {
        ResponseEntity<byte[]> response = lakebaseClient.forward(HttpMethod.POST, "/auth/login", headers, body);
        if (response.getStatusCode().is5xxServerError()) {
            return ResponseEntity.status(401).body("{\"error\":\"Invalid username or password\"}".getBytes());
        }
        return response;
    }
}
