package cloud.dbay.agent.dataagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataAgentService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentTaskRunRepository taskRunRepository;
    private final AgentAuditEventRepository auditEventRepository;
    private final AgentAppRepository appRepository;
    private final AgentWorkspaceRepository workspaceRepository;
    private final AgentEvidencePacketRepository evidencePacketRepository;
    private final ObjectMapper objectMapper;

    public DataAgentService(
            AgentTaskRunRepository taskRunRepository,
            AgentAuditEventRepository auditEventRepository,
            AgentAppRepository appRepository,
            AgentWorkspaceRepository workspaceRepository,
            AgentEvidencePacketRepository evidencePacketRepository,
            ObjectMapper objectMapper
    ) {
        this.taskRunRepository = taskRunRepository;
        this.auditEventRepository = auditEventRepository;
        this.appRepository = appRepository;
        this.workspaceRepository = workspaceRepository;
        this.evidencePacketRepository = evidencePacketRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DataAgentDtos.AgentAppResponse createAgentApp(String tenantKey, DataAgentDtos.CreateAgentAppRequest request) {
        AgentAppEntity entity = new AgentAppEntity();
        entity.setTenantKey(tenantKey);
        entity.setKey(request.key());
        entity.setDisplayName(request.displayName());
        if (request.type() != null && !request.type().isBlank()) entity.setType(request.type());
        return toApp(appRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DataAgentDtos.AgentAppResponse> listAgentApps(String tenantKey) {
        return appRepository.findByTenantKeyOrderByCreatedAtAsc(tenantKey)
                .stream()
                .map(this::toApp)
                .toList();
    }

    @Transactional
    public DataAgentDtos.TaskRunResponse createTaskRunForApp(String tenantKey, String appId, DataAgentDtos.CreateAgentAppRunRequest request) {
        AgentAppEntity app = getApp(tenantKey, appId);
        return createTaskRun(tenantKey, new DataAgentDtos.CreateTaskRunRequest(
                request.goal(),
                request.harnessId() == null || request.harnessId().isBlank() ? app.getKey() : request.harnessId(),
                app.getId()
        ));
    }

    @Transactional
    public DataAgentDtos.TaskRunResponse createTaskRun(String tenantKey, DataAgentDtos.CreateTaskRunRequest request) {
        AgentTaskRunEntity entity = new AgentTaskRunEntity();
        entity.setTenantKey(tenantKey);
        entity.setGoal(request.goal());
        entity.setHarnessId(request.harnessId());
        entity.setAgentAppId(request.agentAppId());
        AgentTaskRunEntity saved = taskRunRepository.save(entity);
        return new DataAgentDtos.TaskRunResponse(saved.getId(), saved.getHarnessId(), saved.getStatus(), saved.getAgentAppId());
    }

    @Transactional(readOnly = true)
    public List<DataAgentDtos.TaskRunSummaryResponse> listTaskRuns(String tenantKey) {
        return taskRunRepository.findByTenantKeyOrderByCreatedAtDesc(tenantKey).stream()
                .map(task -> toSummary(tenantKey, task))
                .toList();
    }

    @Transactional(readOnly = true)
    public DataAgentDtos.TaskRunDetailResponse getTaskRun(String tenantKey, String taskRunId) {
        AgentTaskRunEntity task = getTaskRunEntity(tenantKey, taskRunId);
        List<DataAgentDtos.AuditEventResponse> auditEvents = auditEventRepository
                .findByTenantKeyAndTaskRunIdOrderByCreatedAtAsc(tenantKey, taskRunId)
                .stream()
                .map(this::toAuditEvent)
                .toList();
        DataAgentDtos.WorkspaceResponse workspace = workspaceRepository.findByTenantKeyAndTaskRunId(tenantKey, taskRunId)
                .map(this::toWorkspace)
                .orElse(null);
        List<DataAgentDtos.EvidencePacketResponse> evidencePackets = evidencePacketRepository
                .findByTenantKeyAndTaskRunIdOrderByCreatedAtAsc(tenantKey, taskRunId)
                .stream()
                .map(this::toEvidence)
                .toList();
        return new DataAgentDtos.TaskRunDetailResponse(toSummary(tenantKey, task), workspace, evidencePackets, auditEvents);
    }

    @Transactional
    public DataAgentDtos.WorkspaceResponse createWorkspace(String tenantKey, DataAgentDtos.CreateWorkspaceRequest request) {
        getTaskRunEntity(tenantKey, request.taskRunId());
        AgentWorkspaceEntity entity = new AgentWorkspaceEntity();
        entity.setTenantKey(tenantKey);
        entity.setTaskRunId(request.taskRunId());
        entity.setName(request.name() == null || request.name().isBlank() ? "workspace" : request.name());
        return toWorkspace(workspaceRepository.save(entity));
    }

    @Transactional
    public DataAgentDtos.EvidencePacketResponse createEvidencePacket(String tenantKey, DataAgentDtos.CreateEvidencePacketRequest request) {
        getTaskRunEntity(tenantKey, request.taskRunId());
        AgentEvidencePacketEntity entity = new AgentEvidencePacketEntity();
        entity.setTenantKey(tenantKey);
        entity.setTaskRunId(request.taskRunId());
        entity.setSummary(request.summary());
        return toEvidence(evidencePacketRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public DataAgentDtos.PolicyDecisionResponse checkPermission(String tenantKey, DataAgentDtos.CheckPermissionRequest request) {
        if (request.taskRunId() != null && !request.taskRunId().isBlank()) {
            getTaskRunEntity(tenantKey, request.taskRunId());
        }
        return new DataAgentDtos.PolicyDecisionResponse("ALLOW", request.action(), "default dbay-agent policy");
    }

    @Transactional
    public DataAgentDtos.IdResponse appendAuditEvent(String tenantKey, DataAgentDtos.AppendAuditEventRequest request) {
        getTaskRunEntity(tenantKey, request.taskRunId());
        AgentAuditEventEntity event = new AgentAuditEventEntity();
        event.setTenantKey(tenantKey);
        event.setTaskRunId(request.taskRunId());
        event.setEventType(request.eventType() == null || request.eventType().isBlank() ? "event" : request.eventType());
        event.setPayloadJson(writeJson(request.payload() == null ? Map.of() : request.payload()));
        AgentAuditEventEntity saved = auditEventRepository.save(event);
        return new DataAgentDtos.IdResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<DataAgentDtos.AuditEventResponse> listAuditEvents(String tenantKey, String taskRunId) {
        getTaskRunEntity(tenantKey, taskRunId);
        return auditEventRepository.findByTenantKeyAndTaskRunIdOrderByCreatedAtAsc(tenantKey, taskRunId)
                .stream()
                .map(this::toAuditEvent)
                .toList();
    }

    private AgentTaskRunEntity getTaskRunEntity(String tenantKey, String taskRunId) {
        return taskRunRepository.findByIdAndTenantKey(taskRunId, tenantKey)
                .orElseThrow(() -> new EntityNotFoundException("task run not found: " + taskRunId));
    }

    private DataAgentDtos.TaskRunSummaryResponse toSummary(String tenantKey, AgentTaskRunEntity task) {
        long auditEventCount = auditEventRepository.countByTenantKeyAndTaskRunId(tenantKey, task.getId());
        long evidenceCount = evidencePacketRepository.countByTenantKeyAndTaskRunId(tenantKey, task.getId());
        return new DataAgentDtos.TaskRunSummaryResponse(
                task.getId(),
                task.getGoal(),
                task.getHarnessId(),
                task.getStatus(),
                task.getAgentAppId(),
                0,
                evidenceCount,
                auditEventCount,
                task.getCreatedAt()
        );
    }

    private AgentAppEntity getApp(String tenantKey, String appId) {
        return appRepository.findByIdAndTenantKey(appId, tenantKey)
                .orElseThrow(() -> new EntityNotFoundException("agent app not found: " + appId));
    }

    private DataAgentDtos.AgentAppResponse toApp(AgentAppEntity app) {
        return new DataAgentDtos.AgentAppResponse(
                app.getId(), app.getKey(), app.getDisplayName(), app.getType(), app.getStatus(), app.getCreatedAt());
    }

    private DataAgentDtos.WorkspaceResponse toWorkspace(AgentWorkspaceEntity workspace) {
        return new DataAgentDtos.WorkspaceResponse(
                workspace.getId(),
                workspace.getTaskRunId(),
                workspace.getName(),
                workspace.getRootBranchId(),
                workspace.getCreatedAt());
    }

    private DataAgentDtos.EvidencePacketResponse toEvidence(AgentEvidencePacketEntity evidence) {
        return new DataAgentDtos.EvidencePacketResponse(
                evidence.getId(), evidence.getTaskRunId(), evidence.getSummary(), evidence.getCreatedAt());
    }

    private DataAgentDtos.AuditEventResponse toAuditEvent(AgentAuditEventEntity event) {
        return new DataAgentDtos.AuditEventResponse(
                event.getId(),
                event.getTaskRunId(),
                event.getEventType(),
                readJson(event.getPayloadJson()),
                event.getCreatedAt()
        );
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid audit payload", ex);
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }
}
