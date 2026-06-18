package cloud.dbay.agent;

import cloud.dbay.agent.lakebase.LakebaseProperties;
import cloud.dbay.agent.workload.WorkloadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LakebaseProperties.class, WorkloadProperties.class})
public class DbayAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbayAgentApplication.class, args);
    }
}
