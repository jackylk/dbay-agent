package cloud.dbay.agent.dataagent;

import cloud.dbay.agent.modules.ModuleStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataAgentController {
    @GetMapping("/api/v1/agent-state/task-runs")
    public List<TaskRunResponse> listTaskRuns() {
        return List.of();
    }

    @GetMapping("/api/v1/data-agent/status")
    public ModuleStatus status() {
        return ModuleStatus.active("data-agent", "DataAgent task state APIs are owned by dbay-agent.");
    }

    public record TaskRunResponse(
            String id,
            String goal,
            String status,
            String harnessId,
            Integer branchCount,
            Integer evidenceCount,
            String createdAt
    ) {
    }
}
