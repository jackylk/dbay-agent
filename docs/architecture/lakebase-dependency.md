# Lakebase Dependency

`dbay-agent` treats Lakebase as an external substrate.

## Required Lakebase APIs

- Auth / token introspection
- Tenant lookup
- Database create/list/get/delete
- Schema and table metadata
- SQL execution or connection information
- LakebaseFS folder create/list/get
- LakebaseFS file list/read/write/head
- Usage and quota lookup

## Forbidden Coupling

- Direct SQL reads from Lakebase metadata RDS
- Sharing JPA entities with `lakeon`
- Sharing Kubernetes namespaces
- Importing frontend modules from `lakeon-console`

