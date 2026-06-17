package cloud.dbay.agent.dataagent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DataAgentDtos {
    private DataAgentDtos() {}

    public record CreateTaskRunRequest(
            @NotBlank String goal,
            @JsonProperty("harness_id") @JsonAlias("harnessId") @NotBlank String harnessId,
            @JsonProperty("agent_app_id") @JsonAlias("agentAppId") String agentAppId
    ) {}

    public record TaskRunResponse(
            String id,
            @JsonProperty("harness_id") String harnessId,
            String status,
            @JsonProperty("agent_app_id") String agentAppId
    ) {
        @JsonProperty("harnessId")
        public String harnessIdCamel() { return harnessId; }
        @JsonProperty("agentAppId")
        public String agentAppIdCamel() { return agentAppId; }
    }

    public record TaskRunSummaryResponse(
            String id,
            String goal,
            @JsonProperty("harness_id") String harnessId,
            String status,
            @JsonProperty("agent_app_id") String agentAppId,
            @JsonProperty("branch_count") long branchCount,
            @JsonProperty("evidence_count") long evidenceCount,
            @JsonProperty("audit_event_count") long auditEventCount,
            @JsonProperty("created_at") Instant createdAt
    ) {
        @JsonProperty("harnessId")
        public String harnessIdCamel() { return harnessId; }
        @JsonProperty("agentAppId")
        public String agentAppIdCamel() { return agentAppId; }
        @JsonProperty("branchCount")
        public long branchCountCamel() { return branchCount; }
        @JsonProperty("evidenceCount")
        public long evidenceCountCamel() { return evidenceCount; }
        @JsonProperty("auditEventCount")
        public long auditEventCountCamel() { return auditEventCount; }
        @JsonProperty("createdAt")
        public Instant createdAtCamel() { return createdAt; }
    }

    public record TaskRunDetailResponse(
            TaskRunSummaryResponse task,
            WorkspaceResponse workspace,
            @JsonProperty("evidence_packets") List<EvidencePacketResponse> evidencePackets,
            List<AuditEventResponse> auditEvents
    ) {
        @JsonProperty("audit_events")
        public List<AuditEventResponse> auditEventsSnake() { return auditEvents; }
        @JsonProperty("evidencePackets")
        public List<EvidencePacketResponse> evidencePacketsCamel() { return evidencePackets; }
    }

    public record AppendAuditEventRequest(
            @JsonProperty("task_run_id") @JsonAlias("taskRunId") @NotBlank String taskRunId,
            @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
            Map<String, Object> payload
    ) {}

    public record IdResponse(String id) {}

    public record CreateAgentAppRequest(
            @NotBlank String key,
            @JsonProperty("display_name") @JsonAlias("displayName") @NotBlank String displayName,
            String type
    ) {}

    public record AgentAppResponse(
            String id,
            String key,
            @JsonProperty("display_name") String displayName,
            String type,
            String status,
            @JsonProperty("created_at") Instant createdAt
    ) {
        @JsonProperty("displayName")
        public String displayNameCamel() { return displayName; }
    }

    public record CreateAgentAppRunRequest(
            @NotBlank String goal,
            @JsonProperty("harness_id") @JsonAlias("harnessId") String harnessId
    ) {}

    public record CreateWorkspaceRequest(
            @JsonProperty("task_run_id") @JsonAlias("taskRunId") @NotBlank String taskRunId,
            String name
    ) {}

    public record WorkspaceResponse(
            String id,
            @JsonProperty("task_run_id") String taskRunId,
            String name,
            @JsonProperty("root_branch_id") String rootBranchId,
            @JsonProperty("created_at") Instant createdAt
    ) {
        @JsonProperty("taskRunId")
        public String taskRunIdCamel() { return taskRunId; }
        @JsonProperty("rootBranchId")
        public String rootBranchIdCamel() { return rootBranchId; }
    }

    public record CreateEvidencePacketRequest(
            @JsonProperty("task_run_id") @JsonAlias("taskRunId") @NotBlank String taskRunId,
            String summary
    ) {}

    public record EvidencePacketResponse(
            String id,
            @JsonProperty("task_run_id") String taskRunId,
            String summary,
            @JsonProperty("created_at") Instant createdAt
    ) {
        @JsonProperty("taskRunId")
        public String taskRunIdCamel() { return taskRunId; }
    }

    public record CheckPermissionRequest(
            @JsonProperty("task_run_id") @JsonAlias("taskRunId") String taskRunId,
            String action,
            Map<String, Object> context
    ) {}

    public record PolicyDecisionResponse(
            String decision,
            String action,
            String reason
    ) {}

    public record AuditEventResponse(
            String id,
            @JsonProperty("task_run_id") String taskRunId,
            @JsonProperty("event_type") String eventType,
            Map<String, Object> payload,
            @JsonProperty("created_at") Instant createdAt
    ) {
        @JsonProperty("taskRunId")
        public String taskRunIdCamel() { return taskRunId; }
        @JsonProperty("eventType")
        public String eventTypeCamel() { return eventType; }
        @JsonProperty("createdAt")
        public Instant createdAtCamel() { return createdAt; }
    }
}
