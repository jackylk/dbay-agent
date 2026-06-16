package cloud.dbay.agent.lakebase;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
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
        try {
            return RestClient.create(healthBaseUrl()).get()
                    .uri("/actuator/health")
                    .headers(headers -> {
                        String bearer = bearer();
                        if (!bearer.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, bearer);
                        }
                    })
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "DOWN");
            result.put("error", ex.getClass().getSimpleName());
            return result;
        }
    }

    String bearer() {
        if (properties.serviceToken() == null || properties.serviceToken().isBlank()) {
            return "";
        }
        return "Bearer " + properties.serviceToken();
    }

    String healthBaseUrl() {
        URI uri = URI.create(properties.normalizedApiBaseUrl());
        int port = uri.getPort();
        String authority = port >= 0 ? uri.getHost() + ":" + port : uri.getHost();
        return uri.getScheme() + "://" + authority;
    }
}
