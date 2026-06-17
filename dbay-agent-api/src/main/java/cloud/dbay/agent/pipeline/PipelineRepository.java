package cloud.dbay.agent.pipeline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<PipelineEntity, String> {
    List<PipelineEntity> findByTenantIdAndIsTemplateFalseOrderByCreatedAtDesc(String tenantId);
    List<PipelineEntity> findByIsTemplateTrueOrderByCreatedAtDesc();
    Optional<PipelineEntity> findByIdAndTenantId(String id, String tenantId);
}
