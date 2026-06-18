# DBay Agent E2E

This suite owns the agent-side runtime E2E tests migrated out of `lakeon`.

```bash
python3 -m pytest tests/e2e -v -s
```

The suite covers:

- Knowledge: base creation, ingest, documents, chunks, search, wiki pages
- Memory: base creation, ingest, recall, raw messages, stats
- DataAgent: app, task run, workspace, evidence packet, policy check
- Datalake: dataset, RAY typed job, cancel
- Pipeline: pipeline, version, run, cancel
- Ray: job metadata and placement into `dbay-agent-workers` on CCI
- Notebook: session metadata and placement into `dbay-agent-workers` on CCI

Set `DBAY_AGENT_ENDPOINT` to test a different deployment. The default is
`https://dbay-agent.up.railway.app/agent-api`.
