package cloud.dbay.agent.workload;

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
@Table(name = "dbay_agent_workload_runs", indexes = {
        @Index(name = "idx_dbay_agent_workload_tenant", columnList = "tenant_id"),
        @Index(name = "idx_dbay_agent_workload_kind", columnList = "kind"),
        @Index(name = "idx_dbay_agent_workload_status", columnList = "status")
})
public class WorkloadRunEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "cluster_owner", nullable = false, length = 64)
    private String clusterOwner;

    @Column(name = "cluster_name", nullable = false, length = 128)
    private String clusterName;

    @Column(nullable = false, length = 128)
    private String namespace;

    @Column(nullable = false, length = 32)
    private String backend;

    @Column(columnDefinition = "text")
    private String requestJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            String prefix = "NOTEBOOK".equals(kind) ? "nb_" : "ray_";
            id = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClusterOwner() { return clusterOwner; }
    public void setClusterOwner(String clusterOwner) { this.clusterOwner = clusterOwner; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
