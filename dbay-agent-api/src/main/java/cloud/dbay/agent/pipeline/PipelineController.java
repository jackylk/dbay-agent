package cloud.dbay.agent.pipeline;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {
    private final PipelineRepository pipelineRepository;
    private final PipelineVersionRepository versionRepository;

    public PipelineController(PipelineRepository pipelineRepository, PipelineVersionRepository versionRepository) {
        this.pipelineRepository = pipelineRepository;
        this.versionRepository = versionRepository;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String dagYaml = required(body, "dag_yaml");
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setTenantId(TenantResolver.resolve(request));
        pipeline.setName(required(body, "name"));
        pipeline.setDescription(string(body, "description"));
        setIfPresent(body, "data_type", pipeline::setDataType);
        pipeline.setSourceTemplateId(string(body, "source_template_id"));
        pipeline = pipelineRepository.save(pipeline);

        PipelineVersionEntity version = new PipelineVersionEntity();
        version.setPipelineId(pipeline.getId());
        version.setVersion(1);
        version.setDagYaml(dagYaml);
        version.setChangelog("Initial version");
        versionRepository.save(version);
        return response(pipeline);
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        return pipelineRepository.findByTenantIdAndIsTemplateFalseOrderByCreatedAtDesc(TenantResolver.resolve(request)).stream()
                .map(this::response)
                .toList();
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> stored = pipelineRepository.findByIsTemplateTrueOrderByCreatedAtDesc().stream()
                .map(this::response)
                .toList();
        return java.util.stream.Stream.concat(staticTemplates().stream(), stored.stream()).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable String id) {
        return response(getOwnedOrTemplate(request, id));
    }

    @GetMapping("/{id}/versions")
    public List<Map<String, Object>> versions(HttpServletRequest request, @PathVariable String id) {
        getOwnedOrTemplate(request, id);
        return versionRepository.findByPipelineIdOrderByVersionDesc(id).stream()
                .map(this::versionResponse)
                .toList();
    }

    @GetMapping("/{id}/versions/{version}")
    public Map<String, Object> version(HttpServletRequest request, @PathVariable String id, @PathVariable String version) {
        PipelineEntity pipeline = getOwnedOrTemplate(request, id);
        int number = "latest".equalsIgnoreCase(version) ? pipeline.getLatestVersion() : parseVersion(version);
        return versionResponse(versionRepository.findByPipelineIdAndVersion(id, number)
                .orElseThrow(() -> new EntityNotFoundException("Pipeline version not found: " + id + " v" + number)));
    }

    @PostMapping("/{id}/versions")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createVersion(HttpServletRequest request, @PathVariable String id,
                                             @RequestBody Map<String, Object> body) {
        PipelineEntity pipeline = getOwned(request, id);
        int next = pipeline.getLatestVersion() + 1;
        pipeline.setLatestVersion(next);
        pipelineRepository.save(pipeline);

        PipelineVersionEntity version = new PipelineVersionEntity();
        version.setPipelineId(id);
        version.setVersion(next);
        version.setDagYaml(required(body, "dag_yaml"));
        version.setChangelog(string(body, "changelog"));
        return versionResponse(versionRepository.save(version));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable String id) {
        PipelineEntity pipeline = getOwned(request, id);
        versionRepository.deleteAll(versionRepository.findByPipelineIdOrderByVersionDesc(id));
        pipelineRepository.delete(pipeline);
    }

    private PipelineEntity getOwned(HttpServletRequest request, String id) {
        return pipelineRepository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Pipeline not found: " + id));
    }

    private PipelineEntity getOwnedOrTemplate(HttpServletRequest request, String id) {
        return pipelineRepository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .or(() -> pipelineRepository.findById(id).filter(p -> Boolean.TRUE.equals(p.getIsTemplate())))
                .orElseThrow(() -> new EntityNotFoundException("Pipeline not found: " + id));
    }

    private Map<String, Object> response(PipelineEntity p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("data_type", p.getDataType());
        map.put("is_template", p.getIsTemplate());
        map.put("source_template_id", p.getSourceTemplateId());
        map.put("latest_version", p.getLatestVersion());
        map.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        map.put("updated_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return map;
    }

    private List<Map<String, Object>> staticTemplates() {
        return List.of(
                template("pipe_tpl_video_clean", "视频清洗模板", "VIDEO"),
                template("pipe_tpl_text_clean", "文本处理模板", "TEXT")
        );
    }

    private Map<String, Object> template(String id, String name, String dataType) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("description", name);
        map.put("data_type", dataType);
        map.put("is_template", true);
        map.put("source_template_id", null);
        map.put("latest_version", 1);
        return map;
    }

    private Map<String, Object> versionResponse(PipelineVersionEntity v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.getId());
        map.put("pipeline_id", v.getPipelineId());
        map.put("version", v.getVersion());
        map.put("dag_yaml", v.getDagYaml());
        map.put("status", v.getStatus());
        map.put("changelog", v.getChangelog());
        map.put("created_at", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        return map;
    }

    private int parseVersion(String version) {
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pipeline version: " + version);
        }
    }

    private String required(Map<String, Object> body, String key) {
        String value = string(body, key);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
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
