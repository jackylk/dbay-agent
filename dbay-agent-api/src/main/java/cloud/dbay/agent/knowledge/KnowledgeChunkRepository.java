package cloud.dbay.agent.knowledge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, String> {
    List<KnowledgeChunkEntity> findByTenantIdAndKbIdAndDocumentIdOrderByChunkIndexAsc(String tenantId, String kbId, String documentId);
    List<KnowledgeChunkEntity> findByTenantIdAndKbIdOrderByCreatedAtDesc(String tenantId, String kbId);
    long countByTenantIdAndKbIdAndDocumentId(String tenantId, String kbId, String documentId);
}
