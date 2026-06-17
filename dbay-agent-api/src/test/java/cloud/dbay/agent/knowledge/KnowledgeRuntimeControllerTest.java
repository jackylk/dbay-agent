package cloud.dbay.agent.knowledge;

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
        "spring.datasource.url=jdbc:h2:mem:knowledge-runtime-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class KnowledgeRuntimeControllerTest {
    @Autowired
    MockMvc mockMvc;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void ingestsTextIntoDocumentsChunksSearchAndWiki() throws Exception {
        String tenant = "tenant-knowledge-runtime";
        String kbId = id(mockMvc.perform(post("/api/v1/knowledge/bases")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Runtime KB\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        String docId = id(mockMvc.perform(post("/api/v1/knowledge/ingest")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kb_id": "%s",
                                  "title": "DBay Agent Runtime",
                                  "content": "DBay Agent owns knowledge chunks and wiki pages in its own RDS.",
                                  "tags": ["runtime", "migration"]
                                }
                                """.formatted(kbId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kb_id").value(kbId))
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn());

        mockMvc.perform(get("/api/v1/knowledge/documents?kb_id={kbId}", kbId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(docId));

        mockMvc.perform(get("/api/v1/knowledge/bases/{kbId}/documents/{docId}/chunks", kbId, docId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks", hasSize(1)))
                .andExpect(jsonPath("$.chunks[0].content").value("DBay Agent owns knowledge chunks and wiki pages in its own RDS."));

        mockMvc.perform(post("/api/v1/knowledge/search")
                        .header("X-DBay-Tenant-Id", tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kb_id\":\"" + kbId + "\",\"query\":\"wiki pages\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.results[0].document_id").value(docId));

        mockMvc.perform(get("/api/v1/knowledge/wiki/pages?kb_id={kbId}", kbId)
                        .header("X-DBay-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("DBay Agent Runtime"));
    }

    private String id(MvcResult result) throws Exception {
        JsonNode root = mapper.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }
}
