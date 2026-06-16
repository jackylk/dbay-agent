package cloud.dbay.agent.modules;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModuleStatusController {
    @GetMapping("/api/v1/knowledge/status")
    public ModuleStatus knowledgeStatus() {
        return ModuleStatus.active("knowledge", "Knowledge Base APIs are owned by dbay-agent.");
    }

    @GetMapping("/api/v1/memory/status")
    public ModuleStatus memoryStatus() {
        return ModuleStatus.active("memory", "Memory Base APIs are owned by dbay-agent.");
    }

    @GetMapping("/api/v1/data-agent/status")
    public ModuleStatus dataAgentStatus() {
        return ModuleStatus.active("data-agent", "DataAgent task state APIs are owned by dbay-agent.");
    }

    @GetMapping("/api/v1/datalake/status")
    public ModuleStatus datalakeStatus() {
        return ModuleStatus.active("datalake", "Datalake, Ray and Notebook APIs are owned by dbay-agent.");
    }
}
