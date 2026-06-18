package cloud.dbay.agent.workload;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbay-agent.workloads")
public class WorkloadProperties {
    private String clusterOwner = "dbay-agent";
    private String clusterName = "dbay-agent-cce";
    private String namespace = "dbay-agent-workers";
    private String backend = "CCI";

    public String getClusterOwner() { return clusterOwner; }
    public void setClusterOwner(String clusterOwner) { this.clusterOwner = clusterOwner; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
}
