package cloud.dbay.agent.pipeline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRunRepository extends JpaRepository<PipelineRunEntity, String> {
    List<PipelineRunEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<PipelineRunEntity> findByPipelineIdOrderByCreatedAtDesc(String pipelineId);
    Optional<PipelineRunEntity> findByIdAndTenantId(String id, String tenantId);
}
