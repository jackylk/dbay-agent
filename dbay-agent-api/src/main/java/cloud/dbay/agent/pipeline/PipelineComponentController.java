package cloud.dbay.agent.pipeline;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pipeline-components")
public class PipelineComponentController {
    private final PipelineComponentRepository repository;

    public PipelineComponentController(PipelineComponentRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PipelineComponentEntity entity = new PipelineComponentEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(required(body, "name"));
        setIfPresent(body, "type", entity::setType);
        entity.setSpecJson(JsonMaps.stringify(body));
        return response(repository.save(entity));
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        return repository.findByTenantIdOrderByCreatedAtDesc(TenantResolver.resolve(request)).stream()
                .map(this::response)
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable String id) {
        return response(repository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Pipeline component not found: " + id)));
    }

    @GetMapping("/{id}/versions")
    public List<Map<String, Object>> versions(HttpServletRequest request, @PathVariable String id) {
        Map<String, Object> component = get(request, id);
        return List.of(Map.of("version", 1, "component_id", id, "spec", component.get("spec")));
    }

    @GetMapping("/{id}/versions/{version}")
    public Map<String, Object> version(HttpServletRequest request, @PathVariable String id, @PathVariable String version) {
        return Map.of("version", Integer.parseInt(version), "component_id", id, "spec", get(request, id).get("spec"));
    }

    private Map<String, Object> response(PipelineComponentEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("type", entity.getType());
        map.put("spec", JsonMaps.parse(entity.getSpecJson()));
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }

    private String required(Map<String, Object> body, String key) {
        String value = body.get(key) == null ? null : body.get(key).toString();
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }

    private void setIfPresent(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        String value = body.get(key) == null ? null : body.get(key).toString();
        if (value != null && !value.isBlank()) setter.accept(value);
    }
}
