package cloud.dbay.agent.pipeline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineComponentRepository extends JpaRepository<PipelineComponentEntity, String> {
    List<PipelineComponentEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    Optional<PipelineComponentEntity> findByIdAndTenantId(String id, String tenantId);
}
