package cloud.dbay.agent.lakebase;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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

    public ResponseEntity<byte[]> forward(HttpMethod method, String pathAndQuery, HttpHeaders incomingHeaders, byte[] body) {
        return restClient.method(method)
                .uri(pathAndQuery)
                .headers(headers -> copyHeaders(incomingHeaders, headers))
                .body(body == null ? new byte[0] : body)
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .headers(copyResponseHeaders(response.getHeaders()))
                        .body(response.getBody().readAllBytes()));
    }

    private void copyHeaders(HttpHeaders incoming, HttpHeaders outgoing) {
        incoming.forEach((name, values) -> {
            if (!isHopByHopHeader(name)) {
                outgoing.put(name, values);
            }
        });
        if (!outgoing.containsKey(HttpHeaders.AUTHORIZATION)) {
            String bearer = bearer();
            if (!bearer.isBlank()) {
                outgoing.set(HttpHeaders.AUTHORIZATION, bearer);
            }
        }
    }

    private HttpHeaders copyResponseHeaders(HttpHeaders source) {
        HttpHeaders target = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!isHopByHopHeader(name) && !name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                target.put(name, List.copyOf(values));
            }
        });
        return target;
    }

    private boolean isHopByHopHeader(String name) {
        return name.equalsIgnoreCase(HttpHeaders.CONNECTION)
                || name.equalsIgnoreCase("Keep-Alive")
                || name.equalsIgnoreCase(HttpHeaders.PROXY_AUTHENTICATE)
                || name.equalsIgnoreCase(HttpHeaders.PROXY_AUTHORIZATION)
                || name.equalsIgnoreCase(HttpHeaders.TRAILER)
                || name.equalsIgnoreCase(HttpHeaders.UPGRADE)
                || name.equalsIgnoreCase(HttpHeaders.HOST);
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
