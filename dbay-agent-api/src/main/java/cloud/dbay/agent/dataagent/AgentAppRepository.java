package cloud.dbay.agent.dataagent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAppRepository extends JpaRepository<AgentAppEntity, String> {
    List<AgentAppEntity> findByTenantKeyOrderByCreatedAtAsc(String tenantKey);
    Optional<AgentAppEntity> findByIdAndTenantKey(String id, String tenantKey);
}
