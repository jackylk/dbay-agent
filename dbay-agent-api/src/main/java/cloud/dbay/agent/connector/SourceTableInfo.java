package cloud.dbay.agent.connector;

public record SourceTableInfo(String schema, String table, long rowCount) {
}
