package cloud.dbay.agent.pipeline;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class PipelineRunController {
    private final PipelineRepository pipelineRepository;
    private final PipelineVersionRepository versionRepository;
    private final PipelineRunRepository runRepository;

    public PipelineRunController(PipelineRepository pipelineRepository,
                                 PipelineVersionRepository versionRepository,
                                 PipelineRunRepository runRepository) {
        this.pipelineRepository = pipelineRepository;
        this.versionRepository = versionRepository;
        this.runRepository = runRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> trigger(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String pipelineId = required(body, "pipeline_id");
        PipelineEntity pipeline = pipelineRepository.findByIdAndTenantId(pipelineId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Pipeline not found: " + pipelineId));
        int version = body.get("version") instanceof Number n ? n.intValue() : pipeline.getLatestVersion();
        versionRepository.findByPipelineIdAndVersion(pipelineId, version)
                .orElseThrow(() -> new EntityNotFoundException("Pipeline version not found: " + pipelineId + " v" + version));

        PipelineRunEntity run = new PipelineRunEntity();
        run.setTenantId(tenantId);
        run.setPipelineId(pipelineId);
        run.setPipelineVersion(version);
        run.setParametersJson(JsonMaps.stringify(body.get("parameters")));
        return response(runRepository.save(run));
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request,
                                          @RequestParam(name = "pipeline_id", required = false) String pipelineId) {
        String tenantId = TenantResolver.resolve(request);
        List<PipelineRunEntity> runs = pipelineId == null || pipelineId.isBlank()
                ? runRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : runRepository.findByPipelineIdOrderByCreatedAtDesc(pipelineId).stream()
                    .filter(r -> tenantId.equals(r.getTenantId()))
                    .toList();
        return runs.stream().map(this::response).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable String id) {
        return response(getOwned(request, id));
    }

    @GetMapping("/{id}/steps")
    public List<Map<String, Object>> steps(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return List.of();
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(HttpServletRequest request, @PathVariable String id) {
        PipelineRunEntity run = getOwned(request, id);
        if (!"SUCCEEDED".equals(run.getStatus())) {
            run.setStatus("CANCELLED");
            run.setFinishedAt(Instant.now());
            run = runRepository.save(run);
        }
        return response(run);
    }

    private PipelineRunEntity getOwned(HttpServletRequest request, String id) {
        return runRepository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Pipeline run not found: " + id));
    }

    private Map<String, Object> response(PipelineRunEntity run) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", run.getId());
        map.put("pipeline_id", run.getPipelineId());
        map.put("pipeline_version", run.getPipelineVersion());
        map.put("status", run.getStatus());
        map.put("parameters", JsonMaps.parse(run.getParametersJson()));
        map.put("created_at", run.getCreatedAt() != null ? run.getCreatedAt().toString() : null);
        map.put("updated_at", run.getUpdatedAt() != null ? run.getUpdatedAt().toString() : null);
        map.put("finished_at", run.getFinishedAt() != null ? run.getFinishedAt().toString() : null);
        return map;
    }

    private String required(Map<String, Object> body, String key) {
        String value = body.get(key) == null ? null : body.get(key).toString();
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }
}
