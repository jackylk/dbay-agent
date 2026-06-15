package cloud.dbay.agent.lakebase;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LakebaseHealthController {
    private final LakebaseClient client;

    public LakebaseHealthController(LakebaseClient client) {
        this.client = client;
    }

    @GetMapping("/api/v1/lakebase/health")
    public Map<?, ?> lakebaseHealth() {
        return client.health();
    }
}
