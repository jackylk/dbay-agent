package cloud.dbay.agent.datalake;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatalakeJobRepository extends JpaRepository<DatalakeJobEntity, String> {
    List<DatalakeJobEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<DatalakeJobEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
    Optional<DatalakeJobEntity> findByIdAndTenantId(String id, String tenantId);
}
