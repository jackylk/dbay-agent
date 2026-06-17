package cloud.dbay.agent.dataagent;

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
        "spring.datasource.url=jdbc:h2:mem:dataagent-runtime-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DataAgentRuntimeControllerTest {
    @Autowired
    MockMvc mockMvc;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void ownsAgentAppsWorkspaceBranchesEvidenceAndPolicyState() throws Exception {
        String tenant = "tenant-agent-runtime";
        String appId = id(mockMvc.perform(post("/api/v1/agent-state/apps")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "data-agent",
                                  "display_name": "DataAgent",
                                  "type": "data"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("data-agent"))
                .andReturn());

        String taskId = id(mockMvc.perform(post("/api/v1/agent-state/apps/{appId}/runs", appId)
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Analyze source data\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agent_app_id").value(appId))
                .andReturn());

        String workspaceId = id(mockMvc.perform(post("/api/v1/agent-state/workspaces")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task_run_id\":\"" + taskId + "\",\"name\":\"analysis\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.root_branch_id").isString())
                .andReturn());

        String evidenceId = id(mockMvc.perform(post("/api/v1/agent-state/evidence-packets")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task_run_id\":\"" + taskId + "\",\"summary\":\"query result verified\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/agent-state/policy/check")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task_run_id\":\"" + taskId + "\",\"action\":\"read_dataset\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"));

        mockMvc.perform(get("/api/v1/agent-state/task-runs/{taskId}", taskId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.agent_app_id").value(appId))
                .andExpect(jsonPath("$.workspace.id").value(workspaceId))
                .andExpect(jsonPath("$.evidence_packets[0].id").value(evidenceId));
    }

    private String id(MvcResult result) throws Exception {
        JsonNode root = mapper.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }
}
