package cloud.dbay.agent.datalake;

import cloud.dbay.agent.modules.ModuleStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatalakeController {
    @GetMapping("/api/v1/datalake/datasets")
    public List<DatasetResponse> listDatasets() {
        return List.of();
    }

    @GetMapping("/api/v1/datalake/jobs")
    public List<JobResponse> listJobs() {
        return List.of();
    }

    @GetMapping("/api/v1/datalake/status")
    public ModuleStatus status() {
        return ModuleStatus.active("datalake", "Datalake, Ray and Notebook APIs are owned by dbay-agent.");
    }

    public record DatasetResponse(
            String id,
            String name,
            String status,
            String sourceType,
            Long rowCount,
            Long sizeBytes,
            String createdAt
    ) {
    }

    public record JobResponse(
            String id,
            String name,
            String type,
            String status,
            String createdAt,
            String finishedAt
    ) {
    }
}
