package cloud.dbay.agent.memory;

import cloud.dbay.agent.common.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {
    private final MemoryBaseRepository repository;
    private final MemoryItemRepository itemRepository;
    private final MemoryRawMessageRepository rawMessageRepository;
    private final ObjectMapper objectMapper;

    public MemoryController(
            MemoryBaseRepository repository,
            MemoryItemRepository itemRepository,
            MemoryRawMessageRepository rawMessageRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.rawMessageRepository = rawMessageRepository;
        this.objectMapper = objectMapper;
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
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingest(HttpServletRequest request, @PathVariable String id, @RequestBody Map<String, Object> body) {
        MemoryBaseEntity base = getOwned(request, id);
        String content = firstPresent(body, "content", "memory", "message");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        MemoryRawMessageEntity raw = new MemoryRawMessageEntity();
        raw.setTenantId(base.getTenantId());
        raw.setMemoryBaseId(base.getId());
        raw.setContent(content);
        raw.setSource(string(body, "source"));
        rawMessageRepository.save(raw);

        String signal = blankDefault(string(body, "signal"), "memory");
        MemoryItemEntity saved = null;
        if ("memory".equals(signal) || "conversation".equals(signal)) {
            MemoryItemEntity item = new MemoryItemEntity();
            item.setTenantId(base.getTenantId());
            item.setMemoryBaseId(base.getId());
            item.setMemory(content);
            setIfPresent(body, "memory_type", item::setMemoryType);
            setIfPresent(body, "type", item::setMemoryType);
            item.setSource(string(body, "source"));
            saved = itemRepository.save(item);
        }

        base.setMemoryCount((int) itemRepository.countByTenantIdAndMemoryBaseId(base.getTenantId(), base.getId()));
        repository.save(base);
        if ("memory".equals(signal)) {
            Map<String, Object> response = memoryResponse(saved);
            response.put("status", "stored");
            response.put("memory_id", saved.getId());
            return response;
        }
        return Map.of("status", "extracting", "message_id", raw.getId());
    }

    @PostMapping("/bases/{id}/ingest_extracted")
    public Map<String, Object> ingestExtracted(HttpServletRequest request, @PathVariable String id, @RequestBody Map<String, Object> body) {
        MemoryBaseEntity base = getOwned(request, id);
        String messageId = required(body, "message_id");
        rawMessageRepository.findById(messageId)
                .filter(raw -> base.getTenantId().equals(raw.getTenantId()) && base.getId().equals(raw.getMemoryBaseId()))
                .orElseThrow(() -> new EntityNotFoundException("Raw message not found: " + messageId));
        Object rawData = body.get("data");
        Map<?, ?> data = rawData instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> counts = new LinkedHashMap<>();
        storeExtracted(base, data, "facts", "fact", "facts_stored", counts);
        storeExtracted(base, data, "episodes", "episode", "episodes_stored", counts);
        storeExtracted(base, data, "procedural", "procedural", "procedural_stored", counts);
        storeExtracted(base, data, "decisions", "decision", "decisions_stored", counts);
        storeExtracted(base, data, "rejections", "rejection", "rejections_stored", counts);
        storeExtracted(base, data, "conventions", "convention", "conventions_stored", counts);
        base.setMemoryCount((int) itemRepository.countByTenantIdAndMemoryBaseId(base.getTenantId(), base.getId()));
        repository.save(base);
        return counts;
    }

    @PostMapping("/bases/{id}/recall")
    public Map<String, Object> recall(HttpServletRequest request, @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        MemoryBaseEntity base = getOwned(request, id);
        String query = body == null ? "" : string(body, "query");
        String normalized = query == null ? "" : query.toLowerCase();
        java.util.Set<String> types = body == null ? java.util.Set.of() : stringSet(body.get("memory_types"));
        int topK = body == null ? 10 : intValue(body.get("top_k"), 10);
        List<Map<String, Object>> memories = itemRepository.findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(base.getTenantId(), base.getId())
                .stream()
                .filter(item -> normalized.isBlank() || safe(item.getMemory()).toLowerCase().contains(normalized))
                .filter(item -> types.isEmpty() || types.contains(item.getMemoryType()))
                .limit(Math.max(0, topK))
                .map(this::memoryResponse)
                .toList();
        return Map.of("memories", memories, "total", memories.size());
    }

    @GetMapping("/bases/{id}/memories")
    public Map<String, Object> memories(HttpServletRequest request, @PathVariable String id,
                                        @org.springframework.web.bind.annotation.RequestParam(name = "memory_type", required = false) String memoryType,
                                        @org.springframework.web.bind.annotation.RequestParam(name = "offset", defaultValue = "0") int offset,
                                        @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "20") int limit) {
        MemoryBaseEntity base = getOwned(request, id);
        List<Map<String, Object>> memories = itemRepository.findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(base.getTenantId(), base.getId())
                .stream()
                .filter(item -> memoryType == null || memoryType.isBlank() || memoryType.equals(item.getMemoryType()))
                .map(this::memoryResponse)
                .toList();
        int from = Math.max(0, Math.min(offset, memories.size()));
        int to = Math.max(from, Math.min(memories.size(), from + Math.max(0, limit)));
        List<Map<String, Object>> page = memories.subList(from, to);
        return Map.of("memories", page, "items", page, "total", memories.size());
    }

    @DeleteMapping("/bases/{id}/memories/{memoryId}")
    public Map<String, Object> deleteMemory(HttpServletRequest request, @PathVariable String id, @PathVariable String memoryId) {
        MemoryBaseEntity base = getOwned(request, id);
        MemoryItemEntity item = itemRepository.findById(memoryId)
                .filter(candidate -> base.getTenantId().equals(candidate.getTenantId()) && base.getId().equals(candidate.getMemoryBaseId()))
                .orElseThrow(() -> new EntityNotFoundException("Memory not found: " + memoryId));
        itemRepository.delete(item);
        base.setMemoryCount((int) itemRepository.countByTenantIdAndMemoryBaseId(base.getTenantId(), base.getId()));
        repository.save(base);
        return Map.of("status", "deleted");
    }

    @GetMapping("/bases/{id}/stats")
    public Map<String, Object> stats(HttpServletRequest request, @PathVariable String id) {
        MemoryBaseEntity entity = getOwned(request, id);
        long total = itemRepository.countByTenantIdAndMemoryBaseId(entity.getTenantId(), entity.getId());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (MemoryItemEntity item : itemRepository.findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(entity.getTenantId(), entity.getId())) {
            byType.merge(item.getMemoryType(), 1L, Long::sum);
        }
        return Map.of("total", total, "memory_count", total, "trait_count", entity.getTraitCount(), "by_type", byType);
    }

    @PostMapping("/bases/{id}/digest")
    public Map<String, Object> digest(HttpServletRequest request, @PathVariable String id) {
        getOwned(request, id);
        return Map.of("traits_generated", 0, "unreflected_count", 0);
    }

    @PostMapping("/bases/{id}/digest_extracted")
    public Map<String, Object> digestExtracted(HttpServletRequest request, @PathVariable String id, @RequestBody Map<String, Object> body) {
        MemoryBaseEntity base = getOwned(request, id);
        Object rawData = body.get("data");
        Map<?, ?> data = rawData instanceof Map<?, ?> map ? map : Map.of();
        Object traits = data.get("traits");
        int count = traits instanceof List<?> list ? list.size() : 0;
        base.setTraitCount(base.getTraitCount() + count);
        repository.save(base);
        return Map.of("traits_stored", count);
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

    @GetMapping("/bases/{id}/raw_messages")
    public Map<String, Object> rawMessages(HttpServletRequest request, @PathVariable String id) {
        MemoryBaseEntity base = getOwned(request, id);
        List<Map<String, Object>> messages = rawMessageRepository.findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(base.getTenantId(), base.getId())
                .stream()
                .map(this::rawMessageResponse)
                .toList();
        return Map.of("messages", messages, "total", messages.size());
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

    private String firstPresent(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String value = string(body, key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String required(Map<String, Object> body, String key) {
        String value = string(body, key);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }

    private void storeExtracted(MemoryBaseEntity base, Map<?, ?> data, String key, String type, String countKey, Map<String, Object> counts) {
        Object rawItems = data.get(key);
        int count = 0;
        if (rawItems instanceof List<?> items) {
            for (Object raw : items) {
                if (raw instanceof Map<?, ?> itemMap) {
                    Object content = itemMap.get("content");
                    if (content == null || content.toString().isBlank()) continue;
                    MemoryItemEntity item = new MemoryItemEntity();
                    item.setTenantId(base.getTenantId());
                    item.setMemoryBaseId(base.getId());
                    item.setMemoryType(type);
                    item.setMemory(content.toString());
                    item.setMetadataJson(toJson(itemMap));
                    itemRepository.save(item);
                    count++;
                }
            }
        }
        counts.put(countKey, count);
    }

    private java.util.Set<String> stringSet(Object value) {
        if (!(value instanceof List<?> list)) {
            return java.util.Set.of();
        }
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                set.add(item.toString());
            }
        }
        return set;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            Object value = objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, Object.class);
            return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> memoryResponse(MemoryItemEntity item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("memory_base_id", item.getMemoryBaseId());
        map.put("memory_type", item.getMemoryType());
        map.put("memory", item.getMemory());
        map.put("content", item.getMemory());
        map.put("source", item.getSource());
        Map<String, Object> metadata = readJson(item.getMetadataJson());
        if (item.getSource() != null && !item.getSource().isBlank()) {
            metadata.putAll(LbfsMemoryController.metadata(item.getSource(), item.getMemory()));
        }
        map.put("metadata", metadata);
        map.put("created_at", item.getCreatedAt() != null ? item.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> rawMessageResponse(MemoryRawMessageEntity message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", message.getId());
        map.put("memory_base_id", message.getMemoryBaseId());
        map.put("content", message.getContent());
        map.put("source", message.getSource());
        map.put("created_at", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
        return map;
    }
}
