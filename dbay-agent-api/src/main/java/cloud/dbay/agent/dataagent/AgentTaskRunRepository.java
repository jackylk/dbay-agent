package cloud.dbay.agent.dataagent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskRunRepository extends JpaRepository<AgentTaskRunEntity, String> {
    List<AgentTaskRunEntity> findByTenantKeyOrderByCreatedAtDesc(String tenantKey);
    Optional<AgentTaskRunEntity> findByIdAndTenantKey(String id, String tenantKey);
}
