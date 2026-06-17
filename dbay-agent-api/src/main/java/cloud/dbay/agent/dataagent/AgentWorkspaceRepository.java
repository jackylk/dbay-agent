package cloud.dbay.agent.dataagent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkspaceRepository extends JpaRepository<AgentWorkspaceEntity, String> {
    Optional<AgentWorkspaceEntity> findByTenantKeyAndTaskRunId(String tenantKey, String taskRunId);
}
