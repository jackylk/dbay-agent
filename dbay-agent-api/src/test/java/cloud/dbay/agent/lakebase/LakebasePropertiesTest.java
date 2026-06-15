package cloud.dbay.agent.lakebase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LakebasePropertiesTest {
    @Test
    void normalizesTrailingSlash() {
        LakebaseProperties props = new LakebaseProperties("https://api.dbay.cloud:8443/api/v1/", "token");

        assertThat(props.normalizedApiBaseUrl()).isEqualTo("https://api.dbay.cloud:8443/api/v1");
    }

    @Test
    void fallsBackToProductionApiBase() {
        LakebaseProperties props = new LakebaseProperties("", "");

        assertThat(props.normalizedApiBaseUrl()).isEqualTo("https://api.dbay.cloud:8443/api/v1");
    }
}
