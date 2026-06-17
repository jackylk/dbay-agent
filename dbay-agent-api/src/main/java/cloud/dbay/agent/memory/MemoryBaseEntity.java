package cloud.dbay.agent.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dbay_agent_memory_bases", indexes = {
        @Index(name = "idx_dbay_agent_memory_tenant", columnList = "tenant_id")
})
public class MemoryBaseEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 32)
    private String type = "BUILTIN";

    @Column(nullable = false, length = 32)
    private String status = "READY";

    @Column(length = 64)
    private String scene = "CHAT_ASSISTANT";

    @Column(name = "memory_count")
    private Integer memoryCount = 0;

    @Column(name = "trait_count")
    private Integer traitCount = 0;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel = "BAAI/bge-m3";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = "mem_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public Integer getMemoryCount() { return memoryCount; }
    public void setMemoryCount(Integer memoryCount) { this.memoryCount = memoryCount; }
    public Integer getTraitCount() { return traitCount; }
    public void setTraitCount(Integer traitCount) { this.traitCount = traitCount; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
