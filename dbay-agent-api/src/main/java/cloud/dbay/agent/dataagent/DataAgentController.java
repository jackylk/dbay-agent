package cloud.dbay.agent.dataagent;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent-state")
public class DataAgentController {
    private final DataAgentService service;
    private final TenantKeyResolver tenantKeyResolver;

    public DataAgentController(DataAgentService service, TenantKeyResolver tenantKeyResolver) {
        this.service = service;
        this.tenantKeyResolver = tenantKeyResolver;
    }

    @PostMapping("/apps")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.AgentAppResponse createAgentApp(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.CreateAgentAppRequest body
    ) {
        return service.createAgentApp(tenantKeyResolver.resolve(request), body);
    }

    @GetMapping("/apps")
    public java.util.List<DataAgentDtos.AgentAppResponse> listAgentApps(HttpServletRequest request) {
        return service.listAgentApps(tenantKeyResolver.resolve(request));
    }

    @GetMapping("/apps/{appId}")
    public DataAgentDtos.AgentAppResponse getAgentApp(HttpServletRequest request, @PathVariable String appId) {
        return service.getAgentApp(tenantKeyResolver.resolve(request), appId);
    }

    @PostMapping("/apps/{appId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.TaskRunResponse createAgentAppRun(
            HttpServletRequest request,
            @PathVariable String appId,
            @Valid @RequestBody DataAgentDtos.CreateAgentAppRunRequest body
    ) {
        return service.createTaskRunForApp(tenantKeyResolver.resolve(request), appId, body);
    }

    @PostMapping("/task-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.TaskRunResponse createTaskRun(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.CreateTaskRunRequest body
    ) {
        return service.createTaskRun(tenantKeyResolver.resolve(request), body);
    }

    @GetMapping("/task-runs")
    public List<DataAgentDtos.TaskRunSummaryResponse> listTaskRuns(HttpServletRequest request) {
        return service.listTaskRuns(tenantKeyResolver.resolve(request));
    }

    @GetMapping("/task-runs/{taskRunId}")
    public DataAgentDtos.TaskRunDetailResponse getTaskRun(HttpServletRequest request, @PathVariable String taskRunId) {
        return service.getTaskRun(tenantKeyResolver.resolve(request), taskRunId);
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.WorkspaceResponse createWorkspace(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.CreateWorkspaceRequest body
    ) {
        return service.createWorkspace(tenantKeyResolver.resolve(request), body);
    }

    @PostMapping("/evidence-packets")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.EvidencePacketResponse createEvidencePacket(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.CreateEvidencePacketRequest body
    ) {
        return service.createEvidencePacket(tenantKeyResolver.resolve(request), body);
    }

    @PostMapping("/policy/check")
    public DataAgentDtos.PolicyDecisionResponse checkPermission(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.CheckPermissionRequest body
    ) {
        return service.checkPermission(tenantKeyResolver.resolve(request), body);
    }

    @PostMapping("/audit-events")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.IdResponse appendAuditEvent(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.AppendAuditEventRequest body
    ) {
        return service.appendAuditEvent(tenantKeyResolver.resolve(request), body);
    }

    @PostMapping("/audit/events")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAgentDtos.IdResponse appendAuditEventAlias(
            HttpServletRequest request,
            @Valid @RequestBody DataAgentDtos.AppendAuditEventRequest body
    ) {
        return appendAuditEvent(request, body);
    }

    @GetMapping("/task-runs/{taskRunId}/audit-events")
    public List<DataAgentDtos.AuditEventResponse> listAuditEvents(
            HttpServletRequest request,
            @PathVariable String taskRunId
    ) {
        return service.listAuditEvents(tenantKeyResolver.resolve(request), taskRunId);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(EntityNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    public record ErrorResponse(String code, String message) {}
}
