package cloud.dbay.agent.memory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryItemRepository extends JpaRepository<MemoryItemEntity, String> {
    List<MemoryItemEntity> findByTenantIdAndMemoryBaseIdOrderByCreatedAtDesc(String tenantId, String memoryBaseId);
    Optional<MemoryItemEntity> findByTenantIdAndMemoryBaseIdAndSource(String tenantId, String memoryBaseId, String source);
    long countByTenantIdAndMemoryBaseId(String tenantId, String memoryBaseId);
}
