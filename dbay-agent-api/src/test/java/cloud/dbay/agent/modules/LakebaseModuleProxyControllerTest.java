package cloud.dbay.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cloud.dbay.agent.lakebase.LakebaseClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest({LakebaseModuleProxyController.class, ModuleStatusController.class})
class LakebaseModuleProxyControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LakebaseClient lakebaseClient;

    @Test
    void forwardsModulePathAndQueryToLakebaseApi() throws Exception {
        when(lakebaseClient.forward(eq(HttpMethod.GET), eq("/knowledge/bases?limit=20"), any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("[{\"id\":\"kb_1\"}]".getBytes()));

        mockMvc.perform(get("/api/v1/knowledge/bases?limit=20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":\"kb_1\"}]"));
    }

    @Test
    void keepsStatusEndpointLocal() throws Exception {
        mockMvc.perform(get("/api/v1/data-agent/status"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("dbay-agent"));
    }
}
