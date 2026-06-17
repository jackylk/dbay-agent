package cloud.dbay.agent.datalake;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatalakeDatasetRepository extends JpaRepository<DatalakeDatasetEntity, String> {
    List<DatalakeDatasetEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
