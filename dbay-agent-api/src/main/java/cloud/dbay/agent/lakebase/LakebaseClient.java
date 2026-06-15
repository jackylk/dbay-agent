package cloud.dbay.agent.lakebase;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LakebaseClient {
    private final RestClient restClient;
    private final LakebaseProperties properties;

    public LakebaseClient(RestClient.Builder builder, LakebaseProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.normalizedApiBaseUrl()).build();
    }

    public Map<?, ?> health() {
        return restClient.get()
                .uri("/../actuator/health")
                .headers(headers -> {
                    String bearer = bearer();
                    if (!bearer.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, bearer);
                    }
                })
                .retrieve()
                .body(Map.class);
    }

    String bearer() {
        if (properties.serviceToken() == null || properties.serviceToken().isBlank()) {
            return "";
        }
        return "Bearer " + properties.serviceToken();
    }
}
