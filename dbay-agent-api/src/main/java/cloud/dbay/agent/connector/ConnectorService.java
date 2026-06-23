package cloud.dbay.agent.connector;

import cloud.dbay.agent.connector.ConnectorDtos.ConnectorResponse;
import cloud.dbay.agent.connector.ConnectorDtos.ConnectorTestResponse;
import cloud.dbay.agent.connector.ConnectorDtos.CreateConnectorRequest;
import cloud.dbay.agent.connector.ConnectorDtos.PostgresConnectionSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectorService {
    private static final List<String> POSTGRES_CONFIG_KEYS = List.of("host", "port", "dbname", "ssl_mode", "description");

    private final ConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;
    private final ConnectorSecretCrypto crypto;
    private final PostgresConnectorAdapter postgresAdapter;

    public ConnectorService(ConnectorRepository connectorRepository,
                            ObjectMapper objectMapper,
                            ConnectorSecretCrypto crypto,
                            PostgresConnectorAdapter postgresAdapter) {
        this.connectorRepository = connectorRepository;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.postgresAdapter = postgresAdapter;
    }

    @Transactional
    public ConnectorResponse create(String tenantId, CreateConnectorRequest request) {
        if (request == null || request.type() == null) {
            throw badRequest("Missing connector field: type");
        }
        if (request.type() != ConnectorType.POSTGRESQL) {
            throw badRequest("Only PostgreSQL connector creation is supported");
        }
        Map<String, Object> config = normalizedPostgresConfig(request.config());
        Map<String, Object> secret = request.secret() == null ? Map.of() : request.secret();
        stringValue(config, "host");
        stringValue(config, "dbname");
        stringValue(secret, "user");
        stringValue(secret, "password");

        ConnectorEntity entity = new ConnectorEntity();
        entity.setTenantId(tenantId);
        entity.setType(request.type());
        entity.setName(requireText(request.name(), "name"));
        entity.setStatus(ConnectorStatus.UNTESTED);
        entity.setConfigJson(writeJson(config));
        entity.setEncryptedSecretJson(crypto.encrypt(writeJson(secret)));
        return toResponse(connectorRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ConnectorResponse> list(String tenantId) {
        return connectorRepository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConnectorResponse get(String tenantId, String id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ConnectorTestResponse test(String tenantId, String id) {
        ConnectorEntity entity = find(tenantId, id);
        Map<String, Object> metadata = postgresAdapter.test(resolvePostgres(entity));
        boolean ok = Boolean.TRUE.equals(metadata.get("ok"));
        entity.setLastTestedAt(Instant.now());
        entity.setStatus(ok ? ConnectorStatus.CONNECTED : ConnectorStatus.FAILED);
        entity.setLastError(ok ? null : String.valueOf(metadata.getOrDefault("error", "Connection failed")));
        connectorRepository.save(entity);
        return new ConnectorTestResponse(ok, entity.getLastError(), metadata);
    }

    @Transactional(readOnly = true)
    public List<SourceTableInfo> listPostgresTables(String tenantId, String id) {
        return postgresAdapter.listTables(resolvePostgres(find(tenantId, id)));
    }

    @Transactional(readOnly = true)
    public PostgresConnectionSnapshot resolvePostgres(String tenantId, String id) {
        return resolvePostgres(find(tenantId, id));
    }

    private PostgresConnectionSnapshot resolvePostgres(ConnectorEntity entity) {
        Map<String, Object> config = readJson(entity.getConfigJson());
        Map<String, Object> secret = readJson(crypto.decrypt(entity.getEncryptedSecretJson()));
        return new PostgresConnectionSnapshot(
                entity.getId(),
                entity.getName(),
                stringValue(config, "host"),
                intValue(config, "port", 5432),
                stringValue(config, "dbname"),
                stringValue(secret, "user"),
                stringValue(secret, "password")
        );
    }

    private ConnectorEntity find(String tenantId, String id) {
        return connectorRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + id));
    }

    private ConnectorResponse toResponse(ConnectorEntity entity) {
        Map<String, Object> config = readJson(entity.getConfigJson());
        return new ConnectorResponse(
                entity.getId(),
                entity.getType(),
                entity.getName(),
                entity.getStatus(),
                config,
                targetSummary(config),
                entity.getLastTestedAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                0L,
                "数据库导入"
        );
    }

    private String targetSummary(Map<String, Object> config) {
        return stringValue(config, "host") + ":" + intValue(config, "port", 5432) + "/" + stringValue(config, "dbname");
    }

    private Map<String, Object> normalizedPostgresConfig(Map<String, Object> config) {
        Map<String, Object> source = config == null ? Map.of() : config;
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String key : POSTGRES_CONFIG_KEYS) {
            if (source.containsKey(key)) {
                normalized.put(key, source.get(key));
            }
        }
        normalized.put("port", intValue(normalized, "port", 5432));
        return normalized;
    }

    private int intValue(Map<String, Object> map, String key, int fallback) {
        return intValue(map.get(key), fallback);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return validPort(number.intValue(), value);
        }
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        try {
            return validPort(Integer.parseInt(value.toString()), value);
        } catch (NumberFormatException e) {
            throw badRequest("Invalid port: " + value);
        }
    }

    private int validPort(int port, Object source) {
        if (port < 1 || port > 65535) {
            throw badRequest("Invalid port: " + source);
        }
        return port;
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw badRequest("Missing connector field: " + key);
        }
        return value.toString();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest("Missing connector field: " + field);
        }
        return value;
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Invalid connector JSON", e);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize connector JSON", e);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
