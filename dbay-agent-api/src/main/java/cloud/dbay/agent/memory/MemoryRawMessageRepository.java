package cloud.dbay.agent.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRawMessageRepository extends JpaRepository<MemoryRawMessageEntity, String> {
    List<MemoryRawMessageEntity> findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(String tenantId, String memoryBaseId);
}
