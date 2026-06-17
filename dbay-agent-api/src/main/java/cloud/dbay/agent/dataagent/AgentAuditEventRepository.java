package cloud.dbay.agent.dataagent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAuditEventRepository extends JpaRepository<AgentAuditEventEntity, String> {
    long countByTenantKeyAndTaskRunId(String tenantKey, String taskRunId);
    List<AgentAuditEventEntity> findByTenantKeyAndTaskRunIdOrderByCreatedAtAsc(String tenantKey, String taskRunId);
}
