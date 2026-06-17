package cloud.dbay.agent.dataagent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_task_runs")
public class AgentTaskRunEntity {
    @Id
    private String id;

    @Column(nullable = false, length = 128)
    private String tenantKey;

    @Column(nullable = false, length = 4096)
    private String goal;

    @Column(nullable = false, length = 128)
    private String harnessId;

    @Column(length = 128)
    private String agentAppId;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null || id.isBlank()) {
            id = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        if (status == null || status.isBlank()) {
            status = "CREATED";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantKey() { return tenantKey; }
    public void setTenantKey(String tenantKey) { this.tenantKey = tenantKey; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public String getHarnessId() { return harnessId; }
    public void setHarnessId(String harnessId) { this.harnessId = harnessId; }
    public String getAgentAppId() { return agentAppId; }
    public void setAgentAppId(String agentAppId) { this.agentAppId = agentAppId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
