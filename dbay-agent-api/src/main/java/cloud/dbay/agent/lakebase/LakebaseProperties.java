package cloud.dbay.agent.lakebase;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lakebase")
public record LakebaseProperties(String apiBaseUrl, String serviceToken) {
    public String normalizedApiBaseUrl() {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return "https://api.dbay.cloud:8443/api/v1";
        }
        return apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }
}
