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
@Table(name = "dbay_agent_evidence_packets", indexes = {
        @Index(name = "idx_dbay_agent_evidence_task", columnList = "tenant_key,task_run_id")
})
public class AgentEvidencePacketEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_key", nullable = false, length = 128)
    private String tenantKey;

    @Column(name = "task_run_id", nullable = false, length = 64)
    private String taskRunId;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = "ev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantKey() { return tenantKey; }
    public void setTenantKey(String tenantKey) { this.tenantKey = tenantKey; }
    public String getTaskRunId() { return taskRunId; }
    public void setTaskRunId(String taskRunId) { this.taskRunId = taskRunId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getCreatedAt() { return createdAt; }
}
