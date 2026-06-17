package cloud.dbay.agent.modules;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:owned-modules-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OwnedModulesControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void knowledgeBasesAreOwnedAndTenantIsolated() throws Exception {
        String id = TestJson.id(mockMvc.perform(post("/api/v1/knowledge/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-k-a")
                        .content("{\"name\":\"KB A\",\"description\":\"owned\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn());

        mockMvc.perform(get("/api/v1/knowledge/bases")
                        .header("X-DBay-Tenant-Id", "tenant-k-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/api/v1/knowledge/bases")
                        .header("X-DBay-Tenant-Id", "tenant-k-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void memoryBasesAreOwned() throws Exception {
        String id = TestJson.id(mockMvc.perform(post("/api/v1/memory/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-m")
                        .content("{\"name\":\"Memory A\",\"scene\":\"DEVELOPER_TOOL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scene").value("DEVELOPER_TOOL"))
                .andReturn());

        mockMvc.perform(get("/api/v1/memory/bases/{id}/stats", id)
                        .header("X-DBay-Tenant-Id", "tenant-m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void datalakeJobsAndDatasetsAreOwned() throws Exception {
        mockMvc.perform(post("/api/v1/datalake/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-d")
                        .content("{\"name\":\"events\",\"source_type\":\"ICEBERG\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source_type").value("ICEBERG"));

        String jobId = TestJson.id(mockMvc.perform(post("/api/v1/datalake/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-d")
                        .content("{\"name\":\"batch\",\"type\":\"RAY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn());

        mockMvc.perform(delete("/api/v1/datalake/jobs/{id}", jobId)
                        .header("X-DBay-Tenant-Id", "tenant-d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void pipelinesAndRunsAreOwned() throws Exception {
        String pipelineId = TestJson.id(mockMvc.perform(post("/api/v1/pipelines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-p")
                        .content("{\"name\":\"pipe\",\"data_type\":\"TEXT\",\"dag_yaml\":\"name: pipe\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latest_version").value(1))
                .andReturn());

        mockMvc.perform(post("/api/v1/pipelines/{id}/versions", pipelineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-p")
                        .content("{\"dag_yaml\":\"name: pipe-v2\",\"changelog\":\"v2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        String runId = TestJson.id(mockMvc.perform(post("/api/v1/pipeline-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", "tenant-p")
                        .content("{\"pipeline_id\":\"" + pipelineId + "\",\"version\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pipeline_id").value(pipelineId))
                .andReturn());

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/cancel", runId)
                        .header("X-DBay-Tenant-Id", "tenant-p"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
