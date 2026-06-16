# DBay Agent CCE

This deployment is separate from the Lakebase CCE used by `dbay.cloud`.

## Required Connectivity

- Outbound HTTPS to `https://api.dbay.cloud:8443`
- Access to Huawei Cloud OBS/LTS/AOM as needed by agent workloads
- No access to Lakebase metadata RDS except through Lakebase APIs
- Ray / Notebook / Datalake batch workloads should use CCI/virtual-kubelet, not
  fixed CCE nodes.

## Namespaces

- `dbay-agent`
- `dbay-agent-workers`

## Deploy API

```bash
deploy/cce/dbay-agent/build-and-push-api.sh
deploy/cce/dbay-agent/deploy.sh
```

The first production target should be a dedicated DBay Agent CCE. Until that
cluster exists, the chart can also be installed into an isolated namespace on
the control-plane CCE by setting `KUBECONFIG`.

## Provision Dedicated CCE

```bash
deploy/cce/dbay-agent/provision-cce.py --execute --initial-nodes 1 --max-nodes 2 --bind-master-eip
deploy/cce/dbay-agent/download-kubeconfig.py
```
