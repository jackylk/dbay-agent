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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/preview")
    public List<Object> preview(@RequestHeader HttpHeaders headers,
                                @RequestBody Map<String, Object> body) {
        String databaseId = required(body, "database_id");
        String sql = previewSql(body);
        Map<String, Object> result = lakebaseQuery(headers, databaseId, sql);
        Object rows = result.get("rows");
        return rows instanceof List<?> list ? List.copyOf(list) : List.of();
    }

    @PostMapping("/{id}/export")
    public Map<String, Object> export(HttpServletRequest request,
                                      @RequestHeader HttpHeaders headers,
                                      @PathVariable String id) {
        DatalakeDatasetEntity entity = owned(request, id);
        Map<String, Object> exporting = response(entity, "EXPORTING");
        try {
            long rows = countRows(headers, entity);
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

    private long countRows(HttpHeaders headers, DatalakeDatasetEntity entity) {
        Map<String, Object> request = JsonMaps.parse(entity.getRequestJson());
        Map<String, Object> result = lakebaseQuery(headers, entity.getDatabaseId(), countSql(request));
        Object rows = result.get("rows");
        if (rows instanceof List<?> rowList && !rowList.isEmpty() && rowList.get(0) instanceof List<?> first && !first.isEmpty()) {
            Object value = first.get(0);
            if (value instanceof Number n) return n.longValue();
            if (value != null) return Long.parseLong(value.toString());
        }
        Object rowCount = result.get("row_count");
        return rowCount instanceof Number n ? n.longValue() : 0L;
    }

    private Map<String, Object> lakebaseQuery(HttpHeaders headers, String databaseId, String sql) {
        try {
            ResponseEntity<byte[]> response = lakebaseClient.forward(
                    HttpMethod.POST,
                    "/databases/" + databaseId + "/query",
                    headers,
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

    private String countSql(Map<String, Object> body) {
        String mode = required(body, "query_mode");
        if ("CUSTOM_SQL".equalsIgnoreCase(mode)) {
            return "SELECT COUNT(*) FROM (" + required(body, "sql") + ") dbay_dataset_count";
        }
        return "SELECT COUNT(*) FROM " + firstTable(body);
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
        map.put("obs_path", entity.getObsPath());
        map.put("download_url", entity.getObsPath() == null ? null : "/api/v1/datasets/" + entity.getId() + "/download");
        map.put("code_snippets", Map.of("python", "import pandas as pd"));
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }
}
