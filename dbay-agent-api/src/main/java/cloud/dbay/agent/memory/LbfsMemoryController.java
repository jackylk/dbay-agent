package cloud.dbay.agent.memory;

import cloud.dbay.agent.common.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/lbfs")
public class LbfsMemoryController {
    private final MemoryBaseRepository baseRepository;
    private final MemoryItemRepository itemRepository;

    public LbfsMemoryController(MemoryBaseRepository baseRepository, MemoryItemRepository itemRepository) {
        this.baseRepository = baseRepository;
        this.itemRepository = itemRepository;
    }

    @PostMapping("/files/put")
    public Map<String, Object> put(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String path = required(body, "path");
        String encoded = required(body, "data_base64");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (isViewFile(path)) {
            return Map.of("path", path, "etag", etag(content), "status", "ignored");
        }
        MemoryBaseEntity base = targetBase(tenantId);

        MemoryItemEntity item = itemRepository.findByTenantIdAndMemoryBaseIdAndSource(tenantId, base.getId(), path)
                .orElseGet(MemoryItemEntity::new);
        item.setTenantId(tenantId);
        item.setMemoryBaseId(base.getId());
        item.setMemory(content);
        item.setMemoryType("lbfs");
        item.setSource(path);
        MemoryItemEntity saved = itemRepository.save(item);

        base.setMemoryCount((int) itemRepository.countByTenantIdAndMemoryBaseId(tenantId, base.getId()));
        baseRepository.save(base);

        return Map.of("path", path, "etag", etag(content), "memory_id", saved.getId(), "base_id", base.getId());
    }

    @PostMapping("/files/delete")
    public Map<String, Object> delete(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String path = required(body, "path");
        MemoryBaseEntity base = targetBase(tenantId);
        itemRepository.findByTenantIdAndMemoryBaseIdAndSource(tenantId, base.getId(), path)
                .ifPresent(itemRepository::delete);
        base.setMemoryCount((int) itemRepository.countByTenantIdAndMemoryBaseId(tenantId, base.getId()));
        baseRepository.save(base);
        return Map.of("path", path, "status", "deleted");
    }

    @GetMapping("/memory-target")
    public Map<String, Object> memoryTarget(HttpServletRequest request) {
        MemoryBaseEntity base = targetBase(TenantResolver.resolve(request));
        return Map.of("base_id", base.getId(), "memory_base_id", base.getId());
    }

    @PostMapping("/memory-target")
    public Map<String, Object> setMemoryTarget(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String baseId = required(body, "base_id");
        MemoryBaseEntity base = baseRepository.findByIdAndTenantId(baseId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory base not found: " + baseId));
        base.setType("LBFS_DERIVED");
        base.setScene("LBFS");
        MemoryBaseEntity saved = baseRepository.save(base);
        return Map.of("base_id", saved.getId(), "memory_base_id", saved.getId());
    }

    private MemoryBaseEntity targetBase(String tenantId) {
        return baseRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(base -> "LBFS_DERIVED".equals(base.getType()))
                .findFirst()
                .orElseGet(() -> {
                    MemoryBaseEntity base = new MemoryBaseEntity();
                    base.setTenantId(tenantId);
                    base.setName("LBFS Derived Memory");
                    base.setType("LBFS_DERIVED");
                    base.setScene("LBFS");
                    return baseRepository.save(base);
                });
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value.toString();
    }

    private String etag(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private boolean isViewFile(String path) {
        return "/memory/MEMORY.md".equals(path) || path.endsWith("/memory/MEMORY.md");
    }

    static Map<String, Object> metadata(String source, String content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_path", source);
        metadata.put("source_etag", source == null ? null : Integer.toHexString(content == null ? 0 : content.hashCode()));
        return metadata;
    }
}
