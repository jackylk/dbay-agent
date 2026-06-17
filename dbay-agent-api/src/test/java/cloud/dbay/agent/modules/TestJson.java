package cloud.dbay.agent.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

final class TestJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestJson() {}

    static String id(MvcResult result) throws Exception {
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }
}
