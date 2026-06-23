package cloud.dbay.agent.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.dbay.agent.connector.ConnectorDtos.CreateConnectorRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {
    private static final String TEST_KEY = "test-key-1234567890abcdef1234567";

    @Mock
    ConnectorRepository connectorRepository;

    @Mock
    PostgresConnectorAdapter postgresAdapter;

    ConnectorSecretCrypto crypto;
    ConnectorService service;

    @BeforeEach
    void setUp() {
        crypto = new ConnectorSecretCrypto(TEST_KEY);
        service = new ConnectorService(connectorRepository, new ObjectMapper(), crypto, postgresAdapter);
    }

    @Test
    void createPostgresConnectorEncryptsSecretAndHidesPassword() {
        when(connectorRepository.save(any(ConnectorEntity.class))).thenAnswer(invocation -> {
            ConnectorEntity entity = invocation.getArgument(0);
            entity.setId("conn_pg001");
            return entity;
        });

        var response = service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL,
                "Source PG",
                postgresConfig(),
                postgresSecret()
        ));

        assertThat(response.id()).isEqualTo("conn_pg001");
        assertThat(response.targetSummary()).isEqualTo("pg.example.com:5432/appdb");
        assertThat(response.config()).doesNotContainKey("password");
        assertThat(response.config()).doesNotContainKey("user");
    }

    @Test
    void createPostgresConnectorWhitelistsConfigFieldsToAvoidSecretEcho() throws Exception {
        ArgumentCaptor<ConnectorEntity> entityCaptor = ArgumentCaptor.forClass(ConnectorEntity.class);
        when(connectorRepository.save(any(ConnectorEntity.class))).thenAnswer(invocation -> {
            ConnectorEntity entity = invocation.getArgument(0);
            entity.setId("conn_pg001");
            return entity;
        });
        Map<String, Object> config = new LinkedHashMap<>(postgresConfig());
        config.put("url", "postgres://postgres:secret@pg.example.com/appdb");
        config.put("source_password", "secret");
        config.put("token", "sensitive");

        var response = service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL,
                "Source PG",
                config,
                postgresSecret()
        ));

        verify(connectorRepository).save(entityCaptor.capture());
        Map<String, Object> storedConfig = new ObjectMapper().readValue(
                entityCaptor.getValue().getConfigJson(),
                new TypeReference<LinkedHashMap<String, Object>>() {}
        );
        assertThat(storedConfig).containsOnlyKeys("host", "port", "dbname");
        assertThat(response.config()).containsOnlyKeys("host", "port", "dbname");
    }

    @Test
    void createPostgresConnectorNormalizesNumericStringPort() throws Exception {
        ArgumentCaptor<ConnectorEntity> entityCaptor = ArgumentCaptor.forClass(ConnectorEntity.class);
        when(connectorRepository.save(any(ConnectorEntity.class))).thenAnswer(invocation -> {
            ConnectorEntity entity = invocation.getArgument(0);
            entity.setId("conn_pg001");
            return entity;
        });

        var response = service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL,
                "Source PG",
                new LinkedHashMap<>(Map.of("host", "pg.example.com", "port", "15432", "dbname", "appdb")),
                postgresSecret()
        ));

        verify(connectorRepository).save(entityCaptor.capture());
        Map<String, Object> storedConfig = new ObjectMapper().readValue(
                entityCaptor.getValue().getConfigJson(),
                new TypeReference<LinkedHashMap<String, Object>>() {}
        );
        assertThat(storedConfig).containsEntry("port", 15432);
        assertThat(response.config()).containsEntry("port", 15432);
        assertThat(response.targetSummary()).isEqualTo("pg.example.com:15432/appdb");
    }

    @Test
    void resolvePostgresSnapshotReadsStoredConfigAndSecret() {
        ConnectorEntity entity = postgresEntity();
        when(connectorRepository.findByIdAndTenantId("conn_pg001", "tn_1")).thenReturn(Optional.of(entity));

        var snapshot = service.resolvePostgres("tn_1", "conn_pg001");

        assertThat(snapshot.host()).isEqualTo("pg.example.com");
        assertThat(snapshot.port()).isEqualTo(5432);
        assertThat(snapshot.dbname()).isEqualTo("appdb");
        assertThat(snapshot.user()).isEqualTo("postgres");
        assertThat(snapshot.password()).isEqualTo("secret");
    }

    @Test
    void listReturnsPostgresConnectorWithSanitizedConfig() {
        when(connectorRepository.findAllByTenantIdOrderByUpdatedAtDesc("tn_1")).thenReturn(List.of(postgresEntity()));

        var responses = service.list("tn_1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).usageCount()).isZero();
        assertThat(responses.get(0).config()).containsEntry("host", "pg.example.com");
        assertThat(responses.get(0).config()).doesNotContainKey("password");
    }

    @Test
    void getCrossTenantMissThrowsNotFound() {
        when(connectorRepository.findByIdAndTenantId("conn_pg001", "tn_2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("tn_2", "conn_pg001"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testUpdatesStatusLastTestedAtLastErrorAndDelegatesToAdapter() {
        ConnectorEntity entity = postgresEntity();
        when(connectorRepository.findByIdAndTenantId("conn_pg001", "tn_1")).thenReturn(Optional.of(entity));
        when(postgresAdapter.test(argThat(snapshot ->
                "pg.example.com".equals(snapshot.host())
                        && snapshot.port() == 5432
                        && "appdb".equals(snapshot.dbname())
                        && "postgres".equals(snapshot.user())
                        && "secret".equals(snapshot.password())
        ))).thenReturn(Map.of("ok", false, "error", "connection refused"));

        var result = service.test("tn_1", "conn_pg001");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("connection refused");
        assertThat(entity.getStatus()).isEqualTo(ConnectorStatus.FAILED);
        assertThat(entity.getLastTestedAt()).isNotNull();
        assertThat(entity.getLastError()).isEqualTo("connection refused");
        verify(connectorRepository).save(entity);
        verify(postgresAdapter).test(any(ConnectorDtos.PostgresConnectionSnapshot.class));
    }

    @Test
    void listPostgresTablesDelegatesToAdapterWithResolvedSnapshot() {
        ConnectorEntity entity = postgresEntity();
        List<SourceTableInfo> tables = List.of(new SourceTableInfo("public", "orders", 42));
        when(connectorRepository.findByIdAndTenantId("conn_pg001", "tn_1")).thenReturn(Optional.of(entity));
        when(postgresAdapter.listTables(any(ConnectorDtos.PostgresConnectionSnapshot.class))).thenReturn(tables);

        var result = service.listPostgresTables("tn_1", "conn_pg001");

        assertThat(result).isEqualTo(tables);
        ArgumentCaptor<ConnectorDtos.PostgresConnectionSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(ConnectorDtos.PostgresConnectionSnapshot.class);
        verify(postgresAdapter).listTables(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().host()).isEqualTo("pg.example.com");
        assertThat(snapshotCaptor.getValue().password()).isEqualTo("secret");
    }

    @Test
    void createRejectsMissingFieldsAndInvalidPort() {
        assertBadRequest(() -> service.create("tn_1", null), "type");
        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                null, "Source PG", postgresConfig(), postgresSecret())), "type");

        Map<String, Object> missingHost = new LinkedHashMap<>(postgresConfig());
        missingHost.remove("host");
        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL, "Source PG", missingHost, postgresSecret())), "host");

        Map<String, Object> missingDb = new LinkedHashMap<>(postgresConfig());
        missingDb.remove("dbname");
        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL, "Source PG", missingDb, postgresSecret())), "dbname");

        Map<String, Object> invalidPort = new LinkedHashMap<>(postgresConfig());
        invalidPort.put("port", "not-a-port");
        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL, "Source PG", invalidPort, postgresSecret())), "port");
    }

    @Test
    void createRejectsOutOfRangePort() {
        Map<String, Object> config = new LinkedHashMap<>(postgresConfig());
        config.put("port", 70000);

        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL, "Source PG", config, postgresSecret())), "port");
    }

    @Test
    void createRejectsMissingSecretFields() {
        Map<String, Object> missingUser = new LinkedHashMap<>(postgresSecret());
        missingUser.remove("user");
        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL, "Source PG", postgresConfig(), missingUser)), "user");

        Map<String, Object> missingPassword = new LinkedHashMap<>(postgresSecret());
        missingPassword.remove("password");
        assertBadRequest(() -> service.create("tn_1", new CreateConnectorRequest(
                ConnectorType.POSTGRESQL, "Source PG", postgresConfig(), missingPassword)), "password");
    }

    private void assertBadRequest(ThrowingCallable callable, String messagePart) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> (ResponseStatusException) ex)
                .satisfies(ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains(messagePart);
                });
    }

    private ConnectorEntity postgresEntity() {
        ConnectorEntity entity = new ConnectorEntity();
        entity.setId("conn_pg001");
        entity.setTenantId("tn_1");
        entity.setType(ConnectorType.POSTGRESQL);
        entity.setName("Source PG");
        entity.setConfigJson("{\"host\":\"pg.example.com\",\"port\":5432,\"dbname\":\"appdb\"}");
        entity.setEncryptedSecretJson(crypto.encrypt("{\"user\":\"postgres\",\"password\":\"secret\"}"));
        return entity;
    }

    private Map<String, Object> postgresConfig() {
        return new LinkedHashMap<>(Map.of("host", "pg.example.com", "port", 5432, "dbname", "appdb"));
    }

    private Map<String, Object> postgresSecret() {
        return new LinkedHashMap<>(Map.of("user", "postgres", "password", "secret"));
    }

    private interface ThrowingCallable {
        void call();
    }
}
