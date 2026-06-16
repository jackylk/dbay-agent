package cloud.dbay.agent.memory;

import cloud.dbay.agent.modules.ModuleStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {
    @GetMapping("/api/v1/memory/bases")
    public List<MemoryBaseResponse> listMemoryBases() {
        return List.of();
    }

    @GetMapping("/api/v1/memory/status")
    public ModuleStatus status() {
        return ModuleStatus.active("memory", "Memory Base APIs are owned by dbay-agent.");
    }

    public record MemoryBaseResponse(
            String id,
            String name,
            String description,
            String status,
            String scene,
            Integer memory_count,
            Integer trait_count,
            String updated_at
    ) {
    }
}
