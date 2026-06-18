package cloud.dbay.agent.workload;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkloadController {
    private final WorkloadRunRepository repository;
    private final WorkloadProperties properties;

    public WorkloadController(WorkloadRunRepository repository, WorkloadProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping("/api/v1/workloads/placement")
    public Map<String, Object> placement() {
        return placementResponse();
    }

    @PostMapping("/api/v1/ray/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submitRayJob(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return response(create(request, body, "RAY", "Ray job"));
    }

    @GetMapping("/api/v1/ray/jobs")
    public List<Map<String, Object>> listRayJobs(HttpServletRequest request) {
        return list(request, "RAY");
    }

    @GetMapping("/api/v1/ray/jobs/{id}")
    public Map<String, Object> getRayJob(HttpServletRequest request, @PathVariable String id) {
        return response(getOwned(request, id, "RAY"));
    }

    @PostMapping("/api/v1/ray/jobs/{id}/cancel")
    public Map<String, Object> cancelRayJob(HttpServletRequest request, @PathVariable String id) {
        return response(cancel(getOwned(request, id, "RAY")));
    }

    @PostMapping("/api/v1/notebooks/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createNotebook(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return response(create(request, body, "NOTEBOOK", "Notebook session"));
    }

    @GetMapping("/api/v1/notebooks/sessions")
    public List<Map<String, Object>> listNotebooks(HttpServletRequest request) {
        return list(request, "NOTEBOOK");
    }

    @GetMapping("/api/v1/notebooks/sessions/{id}")
    public Map<String, Object> getNotebook(HttpServletRequest request, @PathVariable String id) {
        return response(getOwned(request, id, "NOTEBOOK"));
    }

    @DeleteMapping("/api/v1/notebooks/sessions/{id}")
    public Map<String, Object> stopNotebook(HttpServletRequest request, @PathVariable String id) {
        return response(cancel(getOwned(request, id, "NOTEBOOK")));
    }

    private WorkloadRunEntity create(HttpServletRequest request, Map<String, Object> body, String kind, String defaultName) {
        WorkloadRunEntity entity = new WorkloadRunEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setKind(kind);
        entity.setName(string(body, "name") != null ? string(body, "name") : defaultName);
        entity.setClusterOwner(properties.getClusterOwner());
        entity.setClusterName(properties.getClusterName());
        entity.setNamespace(properties.getNamespace());
        entity.setBackend(properties.getBackend());
        entity.setRequestJson(JsonMaps.stringify(body));
        return repository.save(entity);
    }

    private List<Map<String, Object>> list(HttpServletRequest request, String kind) {
        return repository.findByTenantIdAndKindOrderByCreatedAtDesc(TenantResolver.resolve(request), kind).stream()
                .map(this::response)
                .toList();
    }

    private WorkloadRunEntity getOwned(HttpServletRequest request, String id, String kind) {
        return repository.findByIdAndTenantIdAndKind(id, TenantResolver.resolve(request), kind)
                .orElseThrow(() -> new EntityNotFoundException(kind + " workload not found: " + id));
    }

    private WorkloadRunEntity cancel(WorkloadRunEntity entity) {
        entity.setStatus("CANCELLED");
        entity.setFinishedAt(Instant.now());
        return repository.save(entity);
    }

    private Map<String, Object> response(WorkloadRunEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("kind", entity.getKind());
        map.put("name", entity.getName());
        map.put("status", entity.getStatus());
        map.put("placement", placementResponse(entity));
        map.put("request", JsonMaps.parse(entity.getRequestJson()));
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        map.put("finished_at", entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null);
        return map;
    }

    private Map<String, Object> placementResponse() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cluster_owner", properties.getClusterOwner());
        map.put("cluster_name", properties.getClusterName());
        map.put("namespace", properties.getNamespace());
        map.put("backend", properties.getBackend());
        map.put("runs_in_lakebase_cluster", false);
        return map;
    }

    private Map<String, Object> placementResponse(WorkloadRunEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cluster_owner", entity.getClusterOwner());
        map.put("cluster_name", entity.getClusterName());
        map.put("namespace", entity.getNamespace());
        map.put("backend", entity.getBackend());
        map.put("runs_in_lakebase_cluster", false);
        return map;
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }
}
