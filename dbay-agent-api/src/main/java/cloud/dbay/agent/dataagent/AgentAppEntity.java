package cloud.dbay.agent.dataagent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dbay_agent_apps", indexes = {
        @Index(name = "idx_dbay_agent_app_tenant", columnList = "tenant_key")
})
public class AgentAppEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_key", nullable = false, length = 128)
    private String tenantKey;

    @Column(name = "app_key", nullable = false, length = 128)
    private String key;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(length = 64)
    private String type = "custom";

    @Column(length = 64)
    private String status = "active";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantKey() { return tenantKey; }
    public void setTenantKey(String tenantKey) { this.tenantKey = tenantKey; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
