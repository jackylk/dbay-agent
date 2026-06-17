package cloud.dbay.agent.dataagent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvidencePacketRepository extends JpaRepository<AgentEvidencePacketEntity, String> {
    List<AgentEvidencePacketEntity> findByTenantKeyAndTaskRunIdOrderByCreatedAtAsc(String tenantKey, String taskRunId);
    long countByTenantKeyAndTaskRunId(String tenantKey, String taskRunId);
}
