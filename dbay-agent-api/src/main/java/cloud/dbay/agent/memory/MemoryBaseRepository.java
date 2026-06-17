package cloud.dbay.agent.memory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryBaseRepository extends JpaRepository<MemoryBaseEntity, String> {
    List<MemoryBaseEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
    Optional<MemoryBaseEntity> findByIdAndTenantId(String id, String tenantId);
}
