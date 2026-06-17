package cloud.dbay.agent.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dbay_agent_pipeline_components", indexes = {
        @Index(name = "idx_dbay_agent_comp_tenant", columnList = "tenant_id")
})
public class PipelineComponentEntity {
    @Id
    @Column(length = 64)
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(nullable = false, length = 256)
    private String name;
    @Column(length = 64)
    private String type = "custom";
    @Column(columnDefinition = "text")
    private String specJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = "comp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public Instant getCreatedAt() { return createdAt; }
}
