package cloud.dbay.agent.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryItemRepository extends JpaRepository<MemoryItemEntity, String> {
    List<MemoryItemEntity> findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(String tenantId, String memoryBaseId);
    long countByTenantIdAndMemoryBaseId(String tenantId, String memoryBaseId);
}
