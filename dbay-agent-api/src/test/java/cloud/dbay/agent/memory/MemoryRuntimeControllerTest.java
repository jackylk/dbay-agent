package cloud.dbay.agent.memory;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:memory-runtime-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MemoryRuntimeControllerTest {
    @Autowired
    MockMvc mockMvc;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void ingestsRecallsAndListsMemoryItemsAndRawMessages() throws Exception {
        String tenant = "tenant-memory-runtime";
        String memId = id(mockMvc.perform(post("/api/v1/memory/bases")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Runtime Memory\",\"scene\":\"DEVELOPER_TOOL\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        String memoryId = id(mockMvc.perform(post("/api/v1/memory/bases/{id}/ingest", memId)
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "The user wants dbay-agent to own memory runtime APIs.",
                                  "memory_type": "preference",
                                  "source": "chat"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memory").value("The user wants dbay-agent to own memory runtime APIs."))
                .andReturn());

        mockMvc.perform(post("/api/v1/memory/bases/{id}/recall", memId)
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"runtime APIs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.memories[0].id").value(memoryId));

        mockMvc.perform(get("/api/v1/memory/bases/{id}/memories", memId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memories", hasSize(1)))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/memory/bases/{id}/raw_messages", memId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/memory/bases/{id}/stats", memId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    private String id(MvcResult result) throws Exception {
        JsonNode root = mapper.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }
}
