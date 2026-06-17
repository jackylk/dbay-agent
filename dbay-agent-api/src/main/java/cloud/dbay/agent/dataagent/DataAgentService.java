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
    private final ObjectMapper objectMapper;

    public DataAgentService(
            AgentTaskRunRepository taskRunRepository,
            AgentAuditEventRepository auditEventRepository,
            ObjectMapper objectMapper
    ) {
        this.taskRunRepository = taskRunRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
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
        return new DataAgentDtos.TaskRunDetailResponse(toSummary(tenantKey, task), auditEvents);
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
        return new DataAgentDtos.TaskRunSummaryResponse(
                task.getId(),
                task.getGoal(),
                task.getHarnessId(),
                task.getStatus(),
                task.getAgentAppId(),
                0,
                0,
                auditEventCount,
                task.getCreatedAt()
        );
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
