package cloud.dbay.agent.workload;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkloadRunRepository extends JpaRepository<WorkloadRunEntity, String> {
    List<WorkloadRunEntity> findByTenantIdAndKindOrderByCreatedAtDesc(String tenantId, String kind);
    Optional<WorkloadRunEntity> findByIdAndTenantIdAndKind(String id, String tenantId, String kind);
}
