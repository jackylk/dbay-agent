package cloud.dbay.agent.knowledge;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeBaseRepository repository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeShareRepository shareRepository;
    private final ObjectMapper objectMapper;

    public KnowledgeController(
            KnowledgeBaseRepository repository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkRepository chunkRepository,
            KnowledgeShareRepository shareRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.shareRepository = shareRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/bases")
    public List<Map<String, Object>> listBases(HttpServletRequest request) {
        String tenantId = TenantResolver.resolve(request);
        List<Map<String, Object>> owned = repository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .map(kb -> toResponse(kb, false))
                .toList();
        List<Map<String, Object>> shared = shareRepository.findByMemberTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .flatMap(share -> repository.findById(share.getKbId()).stream())
                .map(kb -> toResponse(kb, true))
                .toList();
        return java.util.stream.Stream.concat(owned.stream(), shared.stream()).toList();
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
        String tenantId = TenantResolver.resolve(request);
        KnowledgeBaseEntity entity = getAccessible(tenantId, id);
        return toResponse(entity, !tenantId.equals(entity.getTenantId()));
    }

    @DeleteMapping("/bases/{id}")
    public Map<String, Object> deleteBase(HttpServletRequest request, @PathVariable String id) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeBaseEntity kb = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Knowledge base not found: " + id));
        if (!tenantId.equals(kb.getTenantId()) && canAccess(tenantId, id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shared members cannot delete knowledge bases");
        }
        if (!tenantId.equals(kb.getTenantId())) {
            throw new EntityNotFoundException("Knowledge base not found: " + id);
        }
        repository.delete(kb);
        return Map.of("status", "deleted");
    }

    @PostMapping("/bases/{id}/shares")
    public Map<String, Object> createShare(HttpServletRequest request, @PathVariable String id, @RequestBody Map<String, Object> body) {
        KnowledgeBaseEntity kb = getOwnedOrForbidden(request, id);
        String username = required(body, "username");
        if (shareRepository.findByKbIdAndMemberTenantId(id, username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Share already exists");
        }
        KnowledgeShareEntity share = new KnowledgeShareEntity();
        share.setKbId(id);
        share.setOwnerTenantId(kb.getTenantId());
        share.setMemberTenantId(username);
        return shareResponse(shareRepository.save(share));
    }

    @GetMapping("/bases/{id}/shares")
    public List<Map<String, Object>> listShares(HttpServletRequest request, @PathVariable String id) {
        getOwnedOrForbidden(request, id);
        return shareRepository.findByKbIdOrderByCreatedAtAsc(id).stream().map(this::shareResponse).toList();
    }

    @DeleteMapping("/bases/{id}/shares/{shareId}")
    public Map<String, Object> deleteShare(HttpServletRequest request, @PathVariable String id, @PathVariable String shareId) {
        getOwnedOrForbidden(request, id);
        KnowledgeShareEntity share = shareRepository.findByIdAndKbId(shareId, id)
                .orElseThrow(() -> new EntityNotFoundException("Share not found: " + shareId));
        shareRepository.delete(share);
        return Map.of("status", "deleted");
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingest(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String kbId = required(body, "kb_id");
        KnowledgeBaseEntity kb = getAccessible(tenantId, kbId);
        String content = string(body, "content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setTitle(blankDefault(string(body, "title"), "Untitled"));
        doc.setContent(content);
        doc.setTagsJson(toJson(body.get("tags")));
        KnowledgeDocumentEntity saved = documentRepository.save(doc);

        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setTenantId(tenantId);
        chunk.setKbId(kbId);
        chunk.setDocumentId(saved.getId());
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunkRepository.save(chunk);

        kb.setDocumentCount((int) documentRepository.countByTenantIdAndKbId(tenantId, kbId));
        repository.save(kb);

        return documentResponse(saved);
    }

    @PostMapping("/wiki/ingest-url")
    public Map<String, Object> ingestUrl(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("title", blankDefault(string(body, "title"), blankDefault(string(body, "url"), "Imported URL")));
        payload.put("content", blankDefault(string(body, "content"), blankDefault(string(body, "url"), "")));
        Map<String, Object> doc = ingest(request, payload);
        return Map.of("document_id", doc.get("id"), "status", doc.get("status"));
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> listDocuments(HttpServletRequest request, @RequestParam(name = "kb_id") String kbId) {
        String tenantId = TenantResolver.resolve(request);
        getAccessible(tenantId, kbId);
        return documentRepository.findByTenantIdAndKbIdOrderByCreatedAtDesc(tenantId, kbId)
                .stream()
                .map(this::documentResponse)
                .toList();
    }

    @GetMapping("/documents/{id}")
    public Map<String, Object> getDocument(HttpServletRequest request, @PathVariable String id) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeDocumentEntity doc = documentRepository.findById(id)
                .filter(candidate -> canAccess(tenantId, candidate.getKbId()))
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        return documentResponse(doc);
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDocument(HttpServletRequest request, @PathVariable String id) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeDocumentEntity doc = documentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        documentRepository.delete(doc);
        repository.findByIdAndTenantId(doc.getKbId(), tenantId).ifPresent(kb -> {
            kb.setDocumentCount((int) documentRepository.countByTenantIdAndKbId(tenantId, doc.getKbId()));
            repository.save(kb);
        });
        return Map.of("status", "deleted");
    }

    @GetMapping("/upload-url")
    public Map<String, Object> uploadUrl(HttpServletRequest request,
                                         @RequestParam(name = "kb_id") String kbId,
                                         @RequestParam String filename) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeBaseEntity kb = getAccessible(tenantId, kbId);
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setTenantId(kb.getTenantId());
        doc.setKbId(kbId);
        doc.setTitle(filename);
        doc.setContent("");
        doc.setStatus("PENDING");
        KnowledgeDocumentEntity saved = documentRepository.save(doc);
        return Map.of(
                "document_id", saved.getId(),
                "filename", filename,
                "upload_url", publicBaseUrl(request) + "/knowledge/uploads/" + saved.getId(),
                "expires_in", 3600
        );
    }

    @PutMapping("/uploads/{id}")
    public Map<String, Object> upload(@PathVariable String id,
                                      @RequestBody byte[] body) {
        KnowledgeDocumentEntity doc = documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        doc.setContent(new String(body, java.nio.charset.StandardCharsets.UTF_8));
        documentRepository.save(doc);
        return Map.of("document_id", id, "status", "uploaded");
    }

    @PostMapping("/documents/{id}/process")
    public Map<String, Object> processDocument(HttpServletRequest request, @PathVariable String id) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeDocumentEntity doc = documentRepository.findById(id)
                .filter(candidate -> canAccess(tenantId, candidate.getKbId()))
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        if (!"READY".equals(doc.getStatus())) {
            KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
            chunk.setTenantId(tenantId);
            chunk.setKbId(doc.getKbId());
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(0);
            chunk.setContent(safe(doc.getContent()));
            chunkRepository.save(chunk);
            doc.setStatus("READY");
            documentRepository.save(doc);
        }
        repository.findById(doc.getKbId()).ifPresent(kb -> {
            kb.setDocumentCount((int) documentRepository.countByTenantIdAndKbId(kb.getTenantId(), doc.getKbId()));
            repository.save(kb);
        });
        return documentResponse(doc);
    }

    @PostMapping("/search")
    public Map<String, Object> search(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String kbId = required(body, "kb_id");
        String query = blankDefault(string(body, "query"), "").toLowerCase();
        getAccessible(tenantId, kbId);
        List<Map<String, Object>> results = chunkRepository.findByTenantIdAndKbIdOrderByCreatedAtDesc(tenantId, kbId)
                .stream()
                .filter(chunk -> query.isBlank() || safe(chunk.getContent()).toLowerCase().contains(query))
                .map(chunk -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", chunk.getId());
                    map.put("kb_id", chunk.getKbId());
                    map.put("document_id", chunk.getDocumentId());
                    map.put("chunk_index", chunk.getChunkIndex());
                    map.put("content", chunk.getContent());
                    map.put("score", 1.0);
                    return map;
                })
                .toList();
        return Map.of("results", results, "total", results.size());
    }

    @GetMapping("/wiki/pages")
    public List<Map<String, Object>> wikiPages(HttpServletRequest request, @org.springframework.web.bind.annotation.RequestParam(name = "kb_id") String kbId) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeBaseEntity kb = getAccessible(tenantId, kbId);
        return documentRepository.findByTenantIdAndKbIdOrderByCreatedAtDesc(kb.getTenantId(), kbId)
                .stream()
                .map(doc -> {
                    Map<String, Object> map = documentResponse(doc);
                    map.put("doc_id", doc.getId());
                    map.put("title", doc.getTitle());
                    map.put("summary", firstLine(doc.getContent()));
                    return map;
                })
                .toList();
    }

    @GetMapping("/wiki/graph")
    public Map<String, Object> wikiGraph() {
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    @PostMapping("/wiki/chat")
    public Map<String, Object> wikiChat(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String tenantId = TenantResolver.resolve(request);
        String kbId = required(body, "kb_id");
        getAccessible(tenantId, kbId);
        String question = blankDefault(string(body, "question"), blankDefault(string(body, "message"), ""));
        return Map.of("answer", question.isBlank() ? "OK" : "Answer: " + question, "status", "ok");
    }

    @GetMapping("/bases/{kbId}/documents/{docId}/chunks")
    public Map<String, Object> documentChunks(HttpServletRequest request, @PathVariable String kbId, @PathVariable String docId) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeBaseEntity kb = getAccessible(tenantId, kbId);
        documentRepository.findByIdAndTenantId(docId, kb.getTenantId())
                .filter(doc -> kbId.equals(doc.getKbId()))
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + docId));
        List<Map<String, Object>> chunks = chunkRepository.findByTenantIdAndKbIdAndDocumentIdOrderByChunkIndexAsc(tenantId, kbId, docId)
                .stream()
                .map(this::chunkResponse)
                .toList();
        return Map.of("chunks", chunks, "total", chunks.size());
    }

    private KnowledgeBaseEntity getOwned(HttpServletRequest request, String id) {
        return repository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Knowledge base not found: " + id));
    }

    private KnowledgeBaseEntity getOwnedOrForbidden(HttpServletRequest request, String id) {
        String tenantId = TenantResolver.resolve(request);
        KnowledgeBaseEntity kb = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Knowledge base not found: " + id));
        if (!tenantId.equals(kb.getTenantId())) {
            if (canAccess(tenantId, id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner can manage shares");
            }
            throw new EntityNotFoundException("Knowledge base not found: " + id);
        }
        return kb;
    }

    private KnowledgeBaseEntity getBaseForTenant(String tenantId, String id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Knowledge base not found: " + id));
    }

    private KnowledgeBaseEntity getAccessible(String tenantId, String id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .or(() -> shareRepository.findByKbIdAndMemberTenantId(id, tenantId)
                        .flatMap(share -> repository.findById(id)))
                .orElseThrow(() -> new EntityNotFoundException("Knowledge base not found: " + id));
    }

    private boolean canAccess(String tenantId, String kbId) {
        return repository.findByIdAndTenantId(kbId, tenantId).isPresent()
                || shareRepository.findByKbIdAndMemberTenantId(kbId, tenantId).isPresent();
    }

    private Map<String, Object> toResponse(KnowledgeBaseEntity entity) {
        return toResponse(entity, false);
    }

    private Map<String, Object> toResponse(KnowledgeBaseEntity entity, boolean shared) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("tenant_id", entity.getTenantId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("type", entity.getType());
        map.put("status", entity.getStatus());
        map.put("document_count", entity.getDocumentCount());
        map.put("database_id", "agent-" + entity.getId());
        map.put("is_shared", shared);
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> shareResponse(KnowledgeShareEntity share) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", share.getId());
        map.put("kb_id", share.getKbId());
        map.put("username", share.getMemberTenantId());
        map.put("role", share.getRole());
        map.put("created_at", share.getCreatedAt() != null ? share.getCreatedAt().toString() : null);
        return map;
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private String required(Map<String, Object> body, String key) {
        String value = string(body, key);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }

    private Map<String, Object> documentResponse(KnowledgeDocumentEntity doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("tenant_id", doc.getTenantId());
        map.put("kb_id", doc.getKbId());
        map.put("title", doc.getTitle());
        map.put("filename", doc.getTitle());
        map.put("status", doc.getStatus());
        map.put("chunks_count", chunkRepository.countByTenantIdAndKbIdAndDocumentId(doc.getTenantId(), doc.getKbId(), doc.getId()));
        map.put("tags", readTags(doc.getTagsJson()));
        map.put("created_at", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
        map.put("updated_at", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> chunkResponse(KnowledgeChunkEntity chunk) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", chunk.getId());
        map.put("kb_id", chunk.getKbId());
        map.put("document_id", chunk.getDocumentId());
        map.put("chunk_index", chunk.getChunkIndex());
        map.put("content", chunk.getContent());
        return map;
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstLine(String value) {
        String safe = safe(value);
        int newline = safe.indexOf('\n');
        return newline >= 0 ? safe.substring(0, newline) : safe;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private Object readTags(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, Object.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String publicBaseUrl(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }
        String prefix = request.getHeader("X-Forwarded-Prefix");
        if (prefix == null || prefix.isBlank()) {
            prefix = "dbay-agent.up.railway.app".equals(host) ? "/agent-api" : "";
        }
        return proto + "://" + host + prefix + "/api/v1";
    }
}
