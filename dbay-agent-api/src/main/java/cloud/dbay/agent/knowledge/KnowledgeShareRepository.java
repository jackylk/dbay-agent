package cloud.dbay.agent.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeShareRepository extends JpaRepository<KnowledgeShareEntity, String> {
    List<KnowledgeShareEntity> findByKbIdOrderByCreatedAtAsc(String kbId);
    List<KnowledgeShareEntity> findByMemberTenantIdOrderByCreatedAtDesc(String memberTenantId);
    Optional<KnowledgeShareEntity> findByKbIdAndMemberTenantId(String kbId, String memberTenantId);
    Optional<KnowledgeShareEntity> findByIdAndKbId(String id, String kbId);
}
