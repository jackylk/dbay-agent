package cloud.dbay.agent.modules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ModuleEndpointTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void listEndpointsReturnEmptyArraysDuringMigration() throws Exception {
        String[] paths = {
                "/api/v1/knowledge/bases",
                "/api/v1/memory/bases",
                "/api/v1/agent-state/task-runs",
                "/api/v1/datalake/datasets",
                "/api/v1/datalake/jobs"
        };

        for (String path : paths) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }

    @Test
    void statusEndpointDeclaresDbayAgentOwnership() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("dbay-agent"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
