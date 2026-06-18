package cloud.dbay.agent.workload;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:workload-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "dbay-agent.workloads.cluster-owner=dbay-agent",
        "dbay-agent.workloads.cluster-name=dbay-agent-cce-e2e",
        "dbay-agent.workloads.namespace=dbay-agent-workers",
        "dbay-agent.workloads.backend=CCI"
})
class WorkloadControllerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @Test
    void rayJobsRunInDbayAgentCciAndAreTenantIsolated() throws Exception {
        String tenant = "tenant-ray";
        String jobId = id(mockMvc.perform(post("/api/v1/ray/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", tenant)
                        .content("{\"name\":\"ray-e2e\",\"entrypoint\":\"python main.py\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("RAY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.placement.cluster_owner").value("dbay-agent"))
                .andExpect(jsonPath("$.placement.cluster_name").value("dbay-agent-cce-e2e"))
                .andExpect(jsonPath("$.placement.namespace").value("dbay-agent-workers"))
                .andExpect(jsonPath("$.placement.backend").value("CCI"))
                .andExpect(jsonPath("$.placement.runs_in_lakebase_cluster").value(false))
                .andReturn());

        mockMvc.perform(get("/api/v1/ray/jobs")
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(jobId));

        mockMvc.perform(get("/api/v1/ray/jobs")
                        .header("X-DBay-Tenant-Id", "other-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/ray/jobs/{id}/cancel", jobId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void notebookSessionsRunInDbayAgentCci() throws Exception {
        String tenant = "tenant-notebook";
        String sessionId = id(mockMvc.perform(post("/api/v1/notebooks/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DBay-Tenant-Id", tenant)
                        .content("{\"name\":\"notebook-e2e\",\"image\":\"jupyter/base-notebook\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("NOTEBOOK"))
                .andExpect(jsonPath("$.placement.cluster_owner").value("dbay-agent"))
                .andExpect(jsonPath("$.placement.namespace").value("dbay-agent-workers"))
                .andExpect(jsonPath("$.placement.backend").value("CCI"))
                .andReturn());

        mockMvc.perform(get("/api/v1/notebooks/sessions/{id}", sessionId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));

        mockMvc.perform(delete("/api/v1/notebooks/sessions/{id}", sessionId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void placementEndpointExposesDbayAgentWorkloadBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/workloads/placement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster_owner").value("dbay-agent"))
                .andExpect(jsonPath("$.namespace").value("dbay-agent-workers"))
                .andExpect(jsonPath("$.backend").value("CCI"))
                .andExpect(jsonPath("$.runs_in_lakebase_cluster").value(false));
    }

    private static String id(MvcResult result) throws Exception {
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }
}
