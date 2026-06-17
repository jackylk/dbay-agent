package cloud.dbay.agent.pipeline;

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
@Table(name = "dbay_agent_pipelines", indexes = {
        @Index(name = "idx_dbay_agent_pipeline_tenant", columnList = "tenant_id"),
        @Index(name = "idx_dbay_agent_pipeline_template", columnList = "is_template")
})
public class PipelineEntity {
    @Id
    @Column(length = 64)
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(nullable = false, length = 256)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "data_type", length = 32)
    private String dataType = "TEXT";
    @Column(name = "is_template")
    private Boolean isTemplate = false;
    @Column(name = "source_template_id", length = 64)
    private String sourceTemplateId;
    @Column(name = "latest_version")
    private Integer latestVersion = 1;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = "pipe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Boolean getIsTemplate() { return isTemplate; }
    public void setIsTemplate(Boolean isTemplate) { this.isTemplate = isTemplate; }
    public String getSourceTemplateId() { return sourceTemplateId; }
    public void setSourceTemplateId(String sourceTemplateId) { this.sourceTemplateId = sourceTemplateId; }
    public Integer getLatestVersion() { return latestVersion; }
    public void setLatestVersion(Integer latestVersion) { this.latestVersion = latestVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
