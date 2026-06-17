package cloud.dbay.agent.pipeline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineVersionRepository extends JpaRepository<PipelineVersionEntity, String> {
    List<PipelineVersionEntity> findByPipelineIdOrderByVersionDesc(String pipelineId);
    Optional<PipelineVersionEntity> findByPipelineIdAndVersion(String pipelineId, Integer version);
}
