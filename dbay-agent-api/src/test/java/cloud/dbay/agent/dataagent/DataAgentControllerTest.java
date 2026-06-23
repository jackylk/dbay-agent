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
    void agentAppRegistryCreatesListsAndFetchesAppMetadata() throws Exception {
        String appJson = mockMvc.perform(post("/api/v1/agent-state/apps")
                        .header("X-DBay-Tenant-Id", "tenant_apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "paperbench",
                                  "displayName": "论文复现实验助手",
                                  "type": "benchmark",
                                  "version": "0.2.0",
                                  "stageSchema": ["paper_parse", "claim_extract", "experiment_run", "evidence_pack", "report_gate"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.key").value("paperbench"))
                .andExpect(jsonPath("$.displayName").value("论文复现实验助手"))
                .andExpect(jsonPath("$.display_name").value("论文复现实验助手"))
                .andExpect(jsonPath("$.version").value("0.2.0"))
                .andExpect(jsonPath("$.stageSchema[1]").value("claim_extract"))
                .andExpect(jsonPath("$.stage_schema[4]").value("report_gate"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String appId = appJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/agent-state/apps")
                        .header("X-DBay-Tenant-Id", "tenant_apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appId))
                .andExpect(jsonPath("$[0].stage_schema[0]").value("paper_parse"));

        mockMvc.perform(get("/api/v1/agent-state/apps/{appId}", appId)
                        .header("X-DBay-Tenant-Id", "tenant_apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId))
                .andExpect(jsonPath("$.key").value("paperbench"));
    }

    @Test
    void emptyTenantGetsBuiltInAgentApps() throws Exception {
        mockMvc.perform(get("/api/v1/agent-state/apps")
                        .header("X-DBay-Tenant-Id", "tenant_builtins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].key").value("data-agent"))
                .andExpect(jsonPath("$[1].key").value("paperbench"))
                .andExpect(jsonPath("$[1].stageSchema[3]").value("evidence_pack"));

        mockMvc.perform(get("/api/v1/agent-state/apps")
                        .header("X-DBay-Tenant-Id", "tenant_builtins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void createsTaskRunBoundToAppDefaultingHarnessIdFromAppKey() throws Exception {
        String appJson = mockMvc.perform(post("/api/v1/agent-state/apps")
                        .header("X-DBay-Tenant-Id", "tenant_app_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"paperbench","displayName":"论文复现实验助手","type":"benchmark"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String appId = appJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/agent-state/apps/{appId}/runs", appId)
                        .header("X-DBay-Tenant-Id", "tenant_app_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal":"verify a paper claim"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.harness_id").value("paperbench"))
                .andExpect(jsonPath("$.agent_app_id").value(appId))
                .andExpect(jsonPath("$.agentAppId").value(appId));
    }

    @Test
    void taskRunSummaryIncludesWorkspaceEvidenceAndAuditPointers() throws Exception {
        String taskJson = mockMvc.perform(post("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goal":"Verify quicksort","harnessId":"paperbench"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId = taskJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        String workspaceJson = mockMvc.perform(post("/api/v1/agent-state/workspaces")
                        .header("X-DBay-Tenant-Id", "tenant_summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskRunId":"%s","name":"attempt-1"}
                                """.formatted(taskId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String workspaceId = workspaceJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String rootBranchId = workspaceJson.replaceAll(".*\\\"root_branch_id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        String evidenceJson = mockMvc.perform(post("/api/v1/agent-state/evidence-packets")
                        .header("X-DBay-Tenant-Id", "tenant_summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskRunId":"%s","summary":"claim evidence"}
                                """.formatted(taskId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String evidenceId = evidenceJson.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/agent-state/audit/events")
                        .header("X-DBay-Tenant-Id", "tenant_summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskRunId":"%s","eventType":"report_gate","payload":{"result":"allowed"}}
                                """.formatted(taskId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/agent-state/task-runs")
                        .header("X-DBay-Tenant-Id", "tenant_summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workspace_id").value(workspaceId))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$[0].branch_count").value(1))
                .andExpect(jsonPath("$[0].latest_branch_id").value(rootBranchId))
                .andExpect(jsonPath("$[0].latest_evidence_packet_id").value(evidenceId))
                .andExpect(jsonPath("$[0].latest_audit_result").value("allowed"));
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
