package cloud.dbay.agent.connector;

import cloud.dbay.agent.common.TenantResolver;
import cloud.dbay.agent.connector.ConnectorDtos.ConnectorResponse;
import cloud.dbay.agent.connector.ConnectorDtos.ConnectorTestResponse;
import cloud.dbay.agent.connector.ConnectorDtos.CreateConnectorRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connectors")
public class ConnectorController {
    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping
    public List<ConnectorResponse> list(HttpServletRequest request) {
        return connectorService.list(TenantResolver.resolve(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectorResponse create(HttpServletRequest request, @RequestBody CreateConnectorRequest body) {
        return connectorService.create(TenantResolver.resolve(request), body);
    }

    @GetMapping("/{id}")
    public ConnectorResponse get(HttpServletRequest request, @PathVariable String id) {
        return connectorService.get(TenantResolver.resolve(request), id);
    }

    @PostMapping("/{id}/test")
    public ConnectorTestResponse test(HttpServletRequest request, @PathVariable String id) {
        return connectorService.test(TenantResolver.resolve(request), id);
    }

    @GetMapping("/{id}/postgres/tables")
    public List<SourceTableInfo> tables(HttpServletRequest request, @PathVariable String id) {
        return connectorService.listPostgresTables(TenantResolver.resolve(request), id);
    }
}
