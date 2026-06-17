package cloud.dbay.agent.dataagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dataagent-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class DataAgentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsTaskRunsInDbayAgentStore() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal":"Build a dashboard","harnessId":"harness_console"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.harnessId").value("harness_console"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(createResponse).contains("CREATED");

        mockMvc.perform(get("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].goal").value("Build a dashboard"))
                .andExpect(jsonPath("$[0].branchCount").value(0))
                .andExpect(jsonPath("$[0].evidenceCount").value(0));
    }

    @Test
    void isolatesTaskRunsByTenantKey() throws Exception {
        mockMvc.perform(post("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_isolated_a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal":"Private task","harnessId":"harness_a"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_isolated_b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void appendsAndListsAuditEvents() throws Exception {
        String taskJson = mockMvc.perform(post("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal":"Audit task","harnessId":"harness_audit"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId = taskJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/agent-state/audit-events")
                        .header("X-DBay-Tenant-Id", "tenant_audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskRunId":"%s","eventType":"policy.check","payload":{"result":"allow"}}
                                """.formatted(taskId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString());

        mockMvc.perform(get("/api/v1/agent-state/task-runs/{taskId}/audit-events", taskId)
                        .header("X-DBay-Tenant-Id", "tenant_audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("policy.check"))
                .andExpect(jsonPath("$[0].payload.result").value("allow"));

        mockMvc.perform(get("/api/v1/agent-state/task-runs/{taskId}", taskId)
                        .header("X-DBay-Tenant-Id", "tenant_audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.auditEventCount").value(1));
    }
}
