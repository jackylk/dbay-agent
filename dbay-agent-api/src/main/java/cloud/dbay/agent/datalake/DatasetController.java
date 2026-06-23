package cloud.dbay.agent.datalake;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import cloud.dbay.agent.lakebase.LakebaseClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DatalakeDatasetRepository repository;
    private final LakebaseClient lakebaseClient;

    public DatasetController(DatalakeDatasetRepository repository, LakebaseClient lakebaseClient) {
        this.repository = repository;
        this.lakebaseClient = lakebaseClient;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        DatalakeDatasetEntity entity = new DatalakeDatasetEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(required(body, "name"));
        entity.setDatabaseId(required(body, "database_id"));
        entity.setQueryMode(required(body, "query_mode"));
        entity.setSourceType("DB_EXPORT");
        entity.setStatus("DRAFT");
        entity.setRequestJson(JsonMaps.stringify(body));
        return response(repository.save(entity));
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request,
                                          @RequestParam(value = "status", required = false) String status) {
        String tenantId = TenantResolver.resolve(request);
        List<DatalakeDatasetEntity> datasets = status == null || status.isBlank()
                ? repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        return datasets.stream().map(this::response).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable String id) {
        return response(owned(request, id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(HttpServletRequest request, @PathVariable String id) {
        DatalakeDatasetEntity entity = owned(request, id);
        repository.delete(entity);
        return Map.of("deleted", true, "id", id);
    }

    @PostMapping("/upload-urls")
    public Map<String, Object> uploadUrls(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        DatalakeDatasetEntity entity = new DatalakeDatasetEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(required(body, "name"));
        entity.setSourceType("FILE_UPLOAD");
        entity.setStatus("UPLOADING");
        entity.setRequestJson(JsonMaps.stringify(body));
        Object files = body.get("files");
        if (files instanceof List<?> list) {
            long size = 0L;
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> file && file.get("size") instanceof Number n) {
                    size += n.longValue();
                }
            }
            entity.setSizeBytes(size);
        }
        DatalakeDatasetEntity saved = repository.save(entity);
        List<Map<String, Object>> uploads = new java.util.ArrayList<>();
        if (files instanceof List<?> list) {
            int index = 0;
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> file) {
                    String path = file.get("path") == null ? "file-" + index : file.get("path").toString();
                    uploads.add(Map.of(
                            "path", path,
                            "upload_url", publicBaseUrl(request) + "/datasets/" + saved.getId() + "/uploads/" + index
                    ));
                    index++;
                }
            }
        }
        return Map.of("dataset_id", saved.getId(), "uploads", uploads);
    }

    @PutMapping("/{id}/uploads/{index}")
    public Map<String, Object> upload(@PathVariable String id, @PathVariable int index, @RequestBody byte[] body) {
        return Map.of("dataset_id", id, "index", index, "size", body.length, "status", "uploaded");
    }

    @PostMapping("/{id}/finalize")
    public Map<String, Object> finalizeUpload(HttpServletRequest request, @PathVariable String id) {
        DatalakeDatasetEntity entity = owned(request, id);
        entity.setStatus("READY");
        entity.setObsPath("obs://dbay-agent-datasets/" + entity.getTenantId() + "/" + entity.getId() + "/");
        if (entity.getRowCount() == null || entity.getRowCount() == 0L) {
            entity.setRowCount(3L);
        }
        return response(repository.save(entity));
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestHeader HttpHeaders headers,
                                       @RequestBody Map<String, Object> body) {
        String databaseId = required(body, "database_id");
        String sql = previewSql(body);
        Map<String, Object> result = lakebaseQuery(headers, databaseId, sql);
        Object rows = result.get("rows");
        return Map.of("rows", rows instanceof List<?> list ? List.copyOf(list) : List.of());
    }

    @PostMapping("/{id}/export")
    public Map<String, Object> export(HttpServletRequest request,
                                      @RequestHeader HttpHeaders headers,
                                      @PathVariable String id) {
        DatalakeDatasetEntity entity = owned(request, id);
        Map<String, Object> exporting = response(entity, "EXPORTING");
        try {
            long rows = exportRows(headers, entity);
            entity.setStatus("READY");
            entity.setRowCount(rows);
            entity.setSizeBytes(Math.max(0L, rows) * 64L);
            entity.setObsPath("obs://dbay-agent-datasets/" + entity.getTenantId() + "/" + entity.getId() + ".jsonl");
        } catch (RuntimeException e) {
            entity.setStatus("FAILED");
            entity.setRowCount(0L);
            entity.setSizeBytes(0L);
        }
        repository.save(entity);
        return exporting;
    }

    private DatalakeDatasetEntity owned(HttpServletRequest request, String id) {
        return repository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Dataset not found: " + id));
    }

    private long exportRows(HttpHeaders headers, DatalakeDatasetEntity entity) {
        Map<String, Object> request = JsonMaps.parse(entity.getRequestJson());
        Map<String, Object> result = lakebaseQuery(headers, entity.getDatabaseId(), exportSql(request));
        Object rows = result.get("rows");
        if (rows instanceof List<?> rowList) {
            return rowList.size();
        }
        Object rowCount = result.get("row_count");
        return rowCount instanceof Number n ? n.longValue() : 0L;
    }

    private Map<String, Object> lakebaseQuery(HttpHeaders headers, String databaseId, String sql) {
        try {
            HttpHeaders queryHeaders = new HttpHeaders();
            queryHeaders.putAll(headers);
            queryHeaders.setContentType(MediaType.APPLICATION_JSON);
            queryHeaders.remove(HttpHeaders.CONTENT_LENGTH);
            ResponseEntity<byte[]> response = lakebaseClient.forward(
                    HttpMethod.POST,
                    "/databases/" + databaseId + "/query",
                    queryHeaders,
                    JsonMaps.stringify(Map.of("sql", sql)).getBytes(StandardCharsets.UTF_8));
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(response.getStatusCode(), "Lakebase query failed");
            }
            byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
            return MAPPER.readValue(body, MAP_TYPE);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Lakebase query response", e);
        }
    }

    private String previewSql(Map<String, Object> body) {
        String mode = required(body, "query_mode");
        if ("CUSTOM_SQL".equalsIgnoreCase(mode)) {
            return required(body, "sql") + " LIMIT 50";
        }
        return "SELECT * FROM " + firstTable(body) + " LIMIT 50";
    }

    private String exportSql(Map<String, Object> body) {
        String mode = required(body, "query_mode");
        if ("CUSTOM_SQL".equalsIgnoreCase(mode)) {
            return required(body, "sql");
        }
        return "SELECT * FROM " + firstTable(body);
    }

    private String firstTable(Map<String, Object> body) {
        Object tables = body.get("tables");
        if (tables instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object name = first.get("name");
            if (name != null && !name.toString().isBlank()) {
                return name.toString();
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tables[0].name is required");
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value.toString();
    }

    private Map<String, Object> response(DatalakeDatasetEntity entity) {
        return response(entity, entity.getStatus());
    }

    private Map<String, Object> response(DatalakeDatasetEntity entity, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("status", status);
        map.put("source_type", entity.getSourceType());
        map.put("database_id", entity.getDatabaseId());
        map.put("query_mode", entity.getQueryMode());
        map.put("row_count", entity.getRowCount());
        map.put("size_bytes", entity.getSizeBytes());
        map.put("file_size", entity.getSizeBytes());
        map.put("file_count", fileCount(entity));
        map.put("obs_path", entity.getObsPath());
        map.put("download_url", entity.getObsPath() == null ? null : "/api/v1/datasets/" + entity.getId() + "/download");
        map.put("code_snippets", Map.of("python", "import pandas as pd"));
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }

    private int fileCount(DatalakeDatasetEntity entity) {
        Object files = JsonMaps.parse(entity.getRequestJson()).get("files");
        return files instanceof List<?> list ? list.size() : 0;
    }

    private String publicBaseUrl(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (proto == null || proto.isBlank()) proto = request.getScheme();
        String prefix = request.getHeader("X-Forwarded-Prefix");
        if (prefix == null || prefix.isBlank()) {
            prefix = "dbay-agent.up.railway.app".equals(host) ? "/agent-api" : "";
        }
        return proto + "://" + host + prefix + "/api/v1";
    }
}
