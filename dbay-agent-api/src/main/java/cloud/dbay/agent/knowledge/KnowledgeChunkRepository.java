package cloud.dbay.agent.knowledge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, String> {
    List<KnowledgeChunkEntity> findByTenantIdAndKbIdAndDocumentIdOrderByChunkIndexAsc(String tenantId, String kbId, String documentId);
    List<KnowledgeChunkEntity> findByTenantIdAndKbIdOrderByCreatedAtDesc(String tenantId, String kbId);
    List<KnowledgeChunkEntity> findByTenantIdAndKbIdOrderByChunkIndexAsc(String tenantId, String kbId);
    java.util.Optional<KnowledgeChunkEntity> findByTenantIdAndKbIdAndDocumentIdAndChunkIndex(String tenantId, String kbId, String documentId, Integer chunkIndex);
    long countByTenantIdAndKbIdAndDocumentId(String tenantId, String kbId, String documentId);
}
