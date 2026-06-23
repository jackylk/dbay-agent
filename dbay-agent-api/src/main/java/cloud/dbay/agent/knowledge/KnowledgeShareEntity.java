package cloud.dbay.agent.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dbay_agent_knowledge_shares", indexes = {
        @Index(name = "idx_dbay_agent_share_kb", columnList = "kb_id"),
        @Index(name = "idx_dbay_agent_share_member", columnList = "member_tenant_id")
})
public class KnowledgeShareEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "kb_id", nullable = false, length = 64)
    private String kbId;

    @Column(name = "owner_tenant_id", nullable = false, length = 128)
    private String ownerTenantId;

    @Column(name = "member_tenant_id", nullable = false, length = 128)
    private String memberTenantId;

    @Column(nullable = false, length = 32)
    private String role = "member";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = "share_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getKbId() { return kbId; }
    public void setKbId(String kbId) { this.kbId = kbId; }
    public String getOwnerTenantId() { return ownerTenantId; }
    public void setOwnerTenantId(String ownerTenantId) { this.ownerTenantId = ownerTenantId; }
    public String getMemberTenantId() { return memberTenantId; }
    public void setMemberTenantId(String memberTenantId) { this.memberTenantId = memberTenantId; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
