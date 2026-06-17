package cloud.dbay.agent.memory;

import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/memory")
public class MemoryController {
    private final MemoryBaseRepository repository;

    public MemoryController(MemoryBaseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/bases")
    public List<Map<String, Object>> listBases(HttpServletRequest request) {
        return repository.findByTenantIdOrderByUpdatedAtDesc(TenantResolver.resolve(request)).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/bases")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createBase(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String name = string(body, "name");
        if (name == null || name.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory base name is required");
        }
        MemoryBaseEntity entity = new MemoryBaseEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(name);
        entity.setDescription(string(body, "description"));
        setIfPresent(body, "type", entity::setType);
        setIfPresent(body, "scene", entity::setScene);
        setIfPresent(body, "embedding_model", entity::setEmbeddingModel);
        return toResponse(repository.save(entity));
    }

    @GetMapping("/bases/{id}")
    public Map<String, Object> getBase(HttpServletRequest request, @PathVariable String id) {
        return toResponse(getOwned(request, id));
    }

    @DeleteMapping("/bases/{id}")
    public Map<String, Object> deleteBase(HttpServletRequest request, @PathVariable String id) {
        repository.delete(getOwned(request, id));
        return Map.of("status", "deleted");
    }

    @PostMapping("/bases/{id}/ingest")
    public Map<String, Object> ingest(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return Map.of("status", "accepted");
    }

    @PostMapping("/bases/{id}/recall")
    public Map<String, Object> recall(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return Map.of("memories", List.of(), "total", 0);
    }

    @GetMapping("/bases/{id}/memories")
    public Map<String, Object> memories(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return Map.of("items", List.of(), "total", 0);
    }

    @GetMapping("/bases/{id}/stats")
    public Map<String, Object> stats(HttpServletRequest request, @PathVariable String id) {
        MemoryBaseEntity entity = getOwned(request, id);
        return Map.of("total", entity.getMemoryCount(), "trait_count", entity.getTraitCount());
    }

    @GetMapping("/bases/{id}/traits")
    public Map<String, Object> traits(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return Map.of("traits", List.of());
    }

    @GetMapping("/bases/{id}/graph")
    public Map<String, Object> graph(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    private MemoryBaseEntity getOwned(HttpServletRequest request, String id) {
        return repository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Memory base not found: " + id));
    }

    private Map<String, Object> toResponse(MemoryBaseEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("tenant_id", entity.getTenantId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("type", entity.getType());
        map.put("status", entity.getStatus());
        map.put("scene", entity.getScene());
        map.put("memory_count", entity.getMemoryCount());
        map.put("trait_count", entity.getTraitCount());
        map.put("embedding_model", entity.getEmbeddingModel());
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
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
