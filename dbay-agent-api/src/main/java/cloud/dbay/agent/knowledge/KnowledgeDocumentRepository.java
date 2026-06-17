package cloud.dbay.agent.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, String> {
    List<KnowledgeDocumentEntity> findByTenantIdAndKbIdOrderByCreatedAtDesc(String tenantId, String kbId);
    Optional<KnowledgeDocumentEntity> findByIdAndTenantId(String id, String tenantId);
    long countByTenantIdAndKbId(String tenantId, String kbId);
}
