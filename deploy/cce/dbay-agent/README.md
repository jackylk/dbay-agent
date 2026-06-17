# DBay Agent CCE

This deployment is separate from the Lakebase CCE used by `dbay.cloud`.

## Required Connectivity

- Outbound HTTPS to `https://api.dbay.cloud:8443`
- Access to Huawei Cloud OBS/LTS/AOM as needed by agent workloads
- Dedicated DBay Agent PostgreSQL RDS for Knowledge, Memory, Datalake and
  Pipeline metadata
- No access to Lakebase metadata RDS except through Lakebase APIs
- Ray / Notebook / Datalake batch workloads should use CCI/virtual-kubelet, not
  fixed CCE nodes.

## Namespaces

- `dbay-agent`
- `dbay-agent-workers`

## Deploy API

```bash
deploy/cce/dbay-agent/provision-rds.py --execute
deploy/cce/dbay-agent/build-and-push-api.sh
deploy/cce/dbay-agent/deploy.sh
```

`provision-rds.py` writes generated local credentials to
`deploy/cce/dbay-agent/.env`, which is ignored by git and automatically read by
`deploy.sh`.

## Provision Dedicated CCE

```bash
deploy/cce/dbay-agent/provision-cce.py --execute --initial-nodes 1 --max-nodes 2 --bind-master-eip
deploy/cce/dbay-agent/download-kubeconfig.py
```
