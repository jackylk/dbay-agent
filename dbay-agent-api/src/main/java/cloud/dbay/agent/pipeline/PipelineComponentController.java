package cloud.dbay.agent.pipeline;

import cloud.dbay.agent.common.JsonMaps;
import cloud.dbay.agent.common.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pipeline-components")
public class PipelineComponentController {
    private final PipelineComponentRepository repository;

    public PipelineComponentController(PipelineComponentRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PipelineComponentEntity entity = new PipelineComponentEntity();
        entity.setTenantId(TenantResolver.resolve(request));
        entity.setName(required(body, "name"));
        entity.setType(string(body, "data_type", string(body, "type", "UNIVERSAL")));
        entity.setSpecJson(JsonMaps.stringify(body));
        return response(repository.save(entity));
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        List<Map<String, Object>> custom = repository.findByTenantIdOrderByCreatedAtDesc(TenantResolver.resolve(request)).stream()
                .map(this::response)
                .toList();
        return java.util.stream.Stream.concat(presets().stream(), custom.stream()).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable String id) {
        for (Map<String, Object> preset : presets()) {
            if (id.equals(preset.get("id"))) return preset;
        }
        return response(repository.findByIdAndTenantId(id, TenantResolver.resolve(request))
                .orElseThrow(() -> new EntityNotFoundException("Pipeline component not found: " + id)));
    }

    @GetMapping("/{id}/versions")
    public List<Map<String, Object>> versions(HttpServletRequest request, @PathVariable String id) {
        Map<String, Object> component = get(request, id);
        return List.of(versionResponse(id, 1, component));
    }

    @GetMapping("/{id}/versions/{version}")
    public Map<String, Object> version(HttpServletRequest request, @PathVariable String id, @PathVariable String version) {
        return versionResponse(id, Integer.parseInt(version), get(request, id));
    }

    private Map<String, Object> response(PipelineComponentEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> spec = JsonMaps.parse(entity.getSpecJson());
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("type", entity.getType());
        map.put("display_name", spec.getOrDefault("display_name", entity.getName()));
        map.put("category", spec.getOrDefault("category", "CUSTOM"));
        map.put("data_type", spec.getOrDefault("data_type", entity.getType()));
        map.put("description", spec.get("description"));
        map.put("entrypoint", spec.get("entrypoint"));
        map.put("execution_mode", spec.getOrDefault("execution_mode", "FUNCTION"));
        map.put("spec", spec);
        map.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> versionResponse(String id, int version, Map<String, Object> component) {
        return Map.of(
                "version", version,
                "component_id", id,
                "status", "PUBLISHED",
                "execution_mode", component.getOrDefault("execution_mode", "FUNCTION"),
                "params_schema", component.getOrDefault("params_schema", component.get("spec").toString()),
                "spec", component.get("spec")
        );
    }

    private List<Map<String, Object>> presets() {
        return List.of(
                preset("comp_video_normalize", "video_normalize", "视频标准化", "DATA_PREP", "VIDEO"),
                preset("comp_video_scene_split", "video_scene_split", "视频场景切分", "EXTRACT", "VIDEO", "threshold"),
                preset("comp_rule_filter", "rule_filter", "规则过滤", "FILTER", "UNIVERSAL"),
                preset("comp_video_crop", "video_crop", "视频裁剪", "CLEAN", "VIDEO"),
                preset("comp_model_filter_mock", "model_filter_mock", "模型过滤", "FILTER", "UNIVERSAL"),
                preset("comp_quality_check", "quality_check", "质量检查", "QC", "UNIVERSAL"),
                preset("comp_video_labeling_mock", "video_labeling_mock", "视频标注", "LABEL", "VIDEO"),
                preset("comp_dataset_publish", "dataset_publish", "数据集发布", "PUBLISH", "UNIVERSAL"),
                preset("comp_text_dedup", "text_dedup", "文本去重", "DATA_PREP", "TEXT"),
                preset("comp_text_clean", "text_clean", "文本清洗", "CLEAN", "TEXT"),
                preset("comp_text_tokenize", "text_tokenize", "文本分词", "EXTRACT", "TEXT"),
                preset("comp_text_quality_score", "text_quality_score", "文本质量评分", "QC", "TEXT")
        );
    }

    private Map<String, Object> preset(String id, String name, String displayName, String category, String dataType) {
        return preset(id, name, displayName, category, dataType, "{}");
    }

    private Map<String, Object> preset(String id, String name, String displayName, String category, String dataType, String paramsSchema) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("display_name", displayName);
        map.put("category", category);
        map.put("data_type", dataType);
        map.put("type", dataType);
        map.put("execution_mode", "FUNCTION");
        map.put("params_schema", paramsSchema);
        map.put("spec", Map.of("params_schema", paramsSchema));
        return map;
    }

    private String required(Map<String, Object> body, String key) {
        String value = body.get(key) == null ? null : body.get(key).toString();
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }

    private void setIfPresent(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        String value = body.get(key) == null ? null : body.get(key).toString();
        if (value != null && !value.isBlank()) setter.accept(value);
    }

    private String string(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
