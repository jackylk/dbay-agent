package cloud.dbay.agent.modules;

public record ModuleStatus(String module, String owner, String status, String detail) {
    public static ModuleStatus active(String module, String detail) {
        return new ModuleStatus(module, "dbay-agent", "ACTIVE", detail);
    }
}
