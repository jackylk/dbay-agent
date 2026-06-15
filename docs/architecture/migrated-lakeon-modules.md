# Migrated Lakeon Modules

This directory is the migration inbox for intelligence-layer code moved out of
`lakeon`.

## Migrated Sources

- `migrated/lakeon-api`: Java backend modules for Knowledge, Memory,
  AgentState, Datalake, Dataset, Notebook, Pipeline, Ray jobs, Connectors, OBS
  connections, and their original unit tests.
- `migrated/lakeon-console`: Vue API clients, views, and components for
  Knowledge, Memory, AgentState, Datalake, and Connectors.
- `migrated/dbay-cli`: CLI commands for KB, Datalake, and Pipeline workflows.
- `migrated/tests/e2e`: E2E suites that validate DBay Agent workloads.
- `migrated/workers`: Python and service workers for knowledge jobs, memory
  service, orchestrator, wiki agent, Mem0 DBay integration, Echomem, and
  reading companion.

## Integration Rule

Migrated code must not be wired directly into the active `dbay-agent-api` build
until it is decoupled from `lakeon` RDS entities and repositories. Each module
should be reintroduced through explicit Lakebase HTTP API clients and
`dbay-agent` owned schemas.

## Lakeon Boundary

`lakeon` keeps Lakebase Core and LakebaseFS. DBay Agent owns Knowledge, Memory,
DataAgent, Datalake, Sources, Ray, Wiki, and Pipeline behavior.
