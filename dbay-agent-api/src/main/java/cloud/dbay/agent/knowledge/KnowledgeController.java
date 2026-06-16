package cloud.dbay.agent.knowledge;

import cloud.dbay.agent.modules.ModuleStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KnowledgeController {
    @GetMapping("/api/v1/knowledge/bases")
    public List<KnowledgeBaseResponse> listKnowledgeBases() {
        return List.of();
    }

    @GetMapping("/api/v1/knowledge/status")
    public ModuleStatus status() {
        return ModuleStatus.active("knowledge", "Knowledge Base APIs are owned by dbay-agent.");
    }

    public record KnowledgeBaseResponse(
            String id,
            String name,
            String description,
            String status,
            String type,
            Integer document_count,
            String updated_at
    ) {
    }
}
