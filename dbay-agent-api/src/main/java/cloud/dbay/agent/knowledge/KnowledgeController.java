package cloud.dbay.agent.knowledge;

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
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeBaseRepository repository;

    public KnowledgeController(KnowledgeBaseRepository repository) {
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
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge base name is required");
        }
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(name);
        entity.setDescription(string(body, "description"));
        String type = string(body, "type");
        if (type != null && !type.isBlank()) entity.setType(type);
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

    @GetMapping("/documents")
    public List<Map<String, Object>> listDocuments() {
        return List.of();
    }

    @PostMapping("/search")
    public Map<String, Object> search() {
        return Map.of("results", List.of(), "total", 0);
    }

    @GetMapping("/wiki/pages")
    public List<Map<String, Object>> wikiPages() {
        return List.of();
    }

    @GetMapping("/wiki/graph")
    public Map<String, Object> wikiGraph() {
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    private KnowledgeBaseEntity getOwned(HttpServletRequest request, String id) {
        return repository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Knowledge base not found: " + id));
    }

    private Map<String, Object> toResponse(KnowledgeBaseEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("tenant_id", entity.getTenantId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("type", entity.getType());
        map.put("status", entity.getStatus());
        map.put("document_count", entity.getDocumentCount());
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }
}
