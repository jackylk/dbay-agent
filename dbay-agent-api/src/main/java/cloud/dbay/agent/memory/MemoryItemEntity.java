package cloud.dbay.agent.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dbay_agent_memory_items", indexes = {
        @Index(name = "idx_dbay_agent_memory_item_base", columnList = "tenant_id,memory_base_id")
})
public class MemoryItemEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "memory_base_id", nullable = false, length = 64)
    private String memoryBaseId;

    @Column(name = "memory_type", length = 64)
    private String memoryType = "note";

    @Column(columnDefinition = "text")
    private String memory;

    @Column(length = 128)
    private String source;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = "memitem_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getMemoryBaseId() { return memoryBaseId; }
    public void setMemoryBaseId(String memoryBaseId) { this.memoryBaseId = memoryBaseId; }
    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
