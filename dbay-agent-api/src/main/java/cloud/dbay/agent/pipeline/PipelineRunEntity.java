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
@Table(name = "dbay_agent_pipeline_runs", indexes = {
        @Index(name = "idx_dbay_agent_run_tenant", columnList = "tenant_id"),
        @Index(name = "idx_dbay_agent_run_pipeline", columnList = "pipeline_id")
})
public class PipelineRunEntity {
    @Id
    @Column(length = 64)
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "pipeline_id", nullable = false, length = 64)
    private String pipelineId;
    @Column(name = "pipeline_version", nullable = false)
    private Integer pipelineVersion;
    @Column(nullable = false, length = 32)
    private String status = "PENDING";
    @Column(columnDefinition = "text")
    private String parametersJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
    public Integer getPipelineVersion() { return pipelineVersion; }
    public void setPipelineVersion(Integer pipelineVersion) { this.pipelineVersion = pipelineVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
