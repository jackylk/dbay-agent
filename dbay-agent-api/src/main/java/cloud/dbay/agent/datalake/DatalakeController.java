package cloud.dbay.agent.datalake;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datalake")
public class DatalakeController {
    private static final Set<String> VALID_JOB_STATUSES = Set.of("PENDING", "STARTING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED");
    private static final Set<String> TERMINAL_JOB_STATUSES = Set.of("SUCCEEDED", "FAILED", "CANCELLED");

    private final DatalakeDatasetRepository datasetRepository;
    private final DatalakeJobRepository jobRepository;

    public DatalakeController(DatalakeDatasetRepository datasetRepository, DatalakeJobRepository jobRepository) {
        this.datasetRepository = datasetRepository;
        this.jobRepository = jobRepository;
    }

    @GetMapping("/datasets")
    public List<Map<String, Object>> listDatasets(HttpServletRequest request) {
        return datasetRepository.findByTenantIdOrderByCreatedAtDesc(TenantResolver.resolve(request)).stream()
                .map(this::datasetResponse)
                .toList();
    }

    @PostMapping("/datasets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createDataset(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        DatalakeDatasetEntity entity = new DatalakeDatasetEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(required(body, "name"));
        setIfPresent(body, "source_type", entity::setSourceType);
        return datasetResponse(datasetRepository.save(entity));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createJob(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        DatalakeJobEntity entity = new DatalakeJobEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(required(body, "name"));
        entity.setType(required(body, "type"));
        entity.setRequestJson(JsonMaps.stringify(body));
        return jobResponse(jobRepository.save(entity));
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> listJobs(HttpServletRequest request,
                                              @RequestParam(value = "status", required = false) String status) {
        String tenantId = TenantResolver.resolve(request);
        if (status != null && !status.isBlank() && !VALID_JOB_STATUSES.contains(status)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job status: " + status);
        }
        List<DatalakeJobEntity> jobs = status == null || status.isBlank()
                ? jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : jobRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        return jobs.stream().map(this::jobResponse).toList();
    }

    @GetMapping("/jobs/{id}")
    public Map<String, Object> getJob(HttpServletRequest request, @PathVariable String id) {
        return jobResponse(getJobOwned(request, id));
    }

    @DeleteMapping("/jobs/{id}")
    public Map<String, Object> cancelJob(HttpServletRequest request, @PathVariable String id) {
        DatalakeJobEntity entity = getJobOwned(request, id);
        if (TERMINAL_JOB_STATUSES.contains(entity.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Job is already terminal");
        }
        entity.setStatus("CANCELLED");
        entity.setFinishedAt(Instant.now());
        return jobResponse(jobRepository.save(entity));
    }

    @PostMapping("/jobs/{id}/resubmit")
    public Map<String, Object> resubmitJob(HttpServletRequest request, @PathVariable String id) {
        DatalakeJobEntity old = getJobOwned(request, id);
        DatalakeJobEntity entity = new DatalakeJobEntity();
        entity.setTenantId(old.getTenantId());
        entity.setName(old.getName());
        entity.setType(old.getType());
        entity.setRequestJson(old.getRequestJson());
        return jobResponse(jobRepository.save(entity));
    }

    @PostMapping("/ai-script/generate")
    public Map<String, Object> generateScript(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        return Map.of("script", "# Generated script placeholder\n# prompt: " + prompt + "\n", "model", "dbay-agent-local");
    }

    @GetMapping("/ai-script/models")
    public List<Map<String, Object>> models() {
        return List.of(Map.of("id", "dbay-agent-local", "name", "DBay Agent Local"));
    }

    private DatalakeJobEntity getJobOwned(HttpServletRequest request, String id) {
        return jobRepository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Datalake job not found: " + id));
    }

    private Map<String, Object> datasetResponse(DatalakeDatasetEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("status", entity.getStatus());
        map.put("source_type", entity.getSourceType());
        map.put("row_count", entity.getRowCount());
        map.put("size_bytes", entity.getSizeBytes());
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> jobResponse(DatalakeJobEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("type", entity.getType());
        map.put("status", entity.getStatus());
        map.put("request", JsonMaps.parse(entity.getRequestJson()));
        map.put("result", JsonMaps.parse(entity.getResultJson()));
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("finished_at", entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null);
        return map;
    }

    private String required(Map<String, Object> body, String key) {
        String value = string(body, key);
        if (value == null || value.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private void setIfPresent(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        String value = string(body, key);
        if (value != null && !value.isBlank()) setter.accept(value);
    }
}
