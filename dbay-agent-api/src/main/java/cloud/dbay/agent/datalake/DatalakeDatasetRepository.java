package cloud.dbay.agent.datalake;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatalakeDatasetRepository extends JpaRepository<DatalakeDatasetEntity, String> {
    List<DatalakeDatasetEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<DatalakeDatasetEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
    Optional<DatalakeDatasetEntity> findByIdAndTenantId(String id, String tenantId);
}
