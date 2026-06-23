package cloud.dbay.agent.datalake;

import cloud.dbay.agent.common.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ray-jobs")
public class RayJobsController {
    private static final Map<String, Map<String, Object>> JOBS = new ConcurrentHashMap<>();

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> submit(HttpServletRequest request,
                                      @RequestParam("script") MultipartFile script,
                                      @RequestParam(value = "name", required = false) String name,
                                      @RequestParam(value = "input", required = false) String input,
                                      @RequestParam(value = "output", required = false) String output,
                                      @RequestParam(value = "workers", required = false) String workers) {
        TenantResolver.resolve(request);
        String id = "dlj_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JOBS.put(id, Map.of(
                "job_id", id,
                "name", name == null ? script.getOriginalFilename() : name,
                "state", "succeeded",
                "returncode", 0,
                "created_at", Instant.now().toString(),
                "input", input == null ? "" : input,
                "output", output == null ? "" : output,
                "workers", workers == null ? "1" : workers
        ));
        return Map.of("job_id", id, "state", "submitted");
    }

    @GetMapping("/{id}")
    public Map<String, Object> status(HttpServletRequest request, @PathVariable String id) {
        TenantResolver.resolve(request);
        Map<String, Object> job = JOBS.get(id);
        if (job == null) {
            throw new jakarta.persistence.EntityNotFoundException("Ray job not found: " + id);
        }
        return job;
    }

    @GetMapping("/{id}/logs")
    public Map<String, Object> logs(HttpServletRequest request, @PathVariable String id) {
        TenantResolver.resolve(request);
        if (!JOBS.containsKey(id)) {
            throw new jakarta.persistence.EntityNotFoundException("Ray job not found: " + id);
        }
        return Map.of("lines", List.of("pyscaler hello from Ray", "done"));
    }
}
