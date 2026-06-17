package cloud.dbay.agent.knowledge;

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
@Table(name = "dbay_agent_knowledge_bases", indexes = {
        @Index(name = "idx_dbay_agent_kb_tenant", columnList = "tenant_id")
})
public class KnowledgeBaseEntity {
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

    @Column(name = "document_count")
    private Integer documentCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = "kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

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
    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
