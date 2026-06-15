# DBay Agent Project

## Scope

This repo owns DBay's intelligence layer:

- DataAgent
- Sources / Connectors
- Knowledge Base
- Memory Base
- Datalake / Ray / Notebook / Pipeline

It depends on Lakebase through stable HTTP APIs exposed by `dbay.cloud`.

## Boundaries

- Do not import Java packages from `lakeon`.
- Do not read `lakeon` RDS tables directly.
- Do not deploy into the Lakebase CCE namespace.
- Use service tokens and documented Lakebase APIs for cross-service calls.

## Expected Structure

```text
dbay-agent-api/       Spring Boot API
dbay-agent-console/   Vue 3 Console
deploy/               Helm and cloud deployment
tests/e2e/            API and workflow E2E tests
docs/                 Architecture and API contracts
```

