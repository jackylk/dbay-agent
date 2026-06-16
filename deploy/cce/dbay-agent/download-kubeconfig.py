#!/usr/bin/env python3
import json
import os

import hwcloud

CLUSTER_NAME = os.environ.get("DBAY_AGENT_CCE_NAME", "dbay-agent-cce")
KUBECONFIG_PATH = os.environ.get(
    "DBAY_AGENT_KUBECONFIG",
    os.path.expanduser("~/.kube/cce-dbay-agent-config"),
)


def main():
    ak, sk, _ = hwcloud.load_credentials()
    project_id = hwcloud.get_project_id(ak, sk)
    cluster = next(
        (item for item in hwcloud.list_clusters(ak, sk, project_id)
         if item.get("metadata", {}).get("name") == CLUSTER_NAME),
        None,
    )
    if not cluster:
        raise SystemExit(f"cluster not found: {CLUSTER_NAME}")
    cluster_id = cluster["metadata"]["uid"]
    body = json.dumps({"duration": -1})
    url = f"https://cce.{hwcloud.REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters/{cluster_id}/clustercert"
    status, data = hwcloud.api("POST", url, ak, sk, body, add_trailing_slash=True)
    if status not in (200, 201):
        raise RuntimeError(f"download kubeconfig failed: {status} {data}")
    if os.environ.get("DBAY_AGENT_KUBECONFIG_INTERNAL") != "1":
        detail = hwcloud.get_cluster(ak, sk, project_id, cluster_id)
        external = next(
            (item.get("url") for item in detail.get("status", {}).get("endpoints", [])
             if item.get("type") == "External"),
            None,
        )
        if external:
            for item in data.get("clusters", []):
                item.setdefault("cluster", {})["server"] = external
    os.makedirs(os.path.dirname(KUBECONFIG_PATH), exist_ok=True)
    with open(KUBECONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    os.chmod(KUBECONFIG_PATH, 0o600)
    print(KUBECONFIG_PATH)


if __name__ == "__main__":
    main()
