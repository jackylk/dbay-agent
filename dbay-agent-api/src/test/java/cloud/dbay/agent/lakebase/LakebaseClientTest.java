package cloud.dbay.agent.lakebase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LakebaseClientTest {
    @Test
    void omitsBlankBearerToken() {
        LakebaseClient client = new LakebaseClient(
                RestClient.builder(),
                new LakebaseProperties("https://api.dbay.cloud:8443/api/v1", "")
        );

        assertThat(client.bearer()).isBlank();
    }

    @Test
    void formatsBearerToken() {
        LakebaseClient client = new LakebaseClient(
                RestClient.builder(),
                new LakebaseProperties("https://api.dbay.cloud:8443/api/v1", "svc-token")
        );

        assertThat(client.bearer()).isEqualTo("Bearer svc-token");
    }

    @Test
    void derivesHealthBaseFromApiBase() {
        LakebaseClient client = new LakebaseClient(
                RestClient.builder(),
                new LakebaseProperties("https://api.dbay.cloud:8443/api/v1", "")
        );

        assertThat(client.healthBaseUrl()).isEqualTo("https://api.dbay.cloud:8443");
    }
}
