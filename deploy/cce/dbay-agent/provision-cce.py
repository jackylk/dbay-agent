#!/usr/bin/env python3
"""Provision the DBay Agent CCE cluster in the same VPC as DBay/Lakebase."""

import argparse
import base64
import json
import os
import secrets
import string
import sys
import time
import urllib.request

from passlib.hash import sha512_crypt

import hwcloud

AGENT_CLUSTER_NAME = "dbay-agent-cce"
AGENT_NODEPOOL_NAME = "dbay-agent-pool"
SOURCE_CLUSTER_NAME = "dbay-cce"
MASTER_PORT = 5443
PASSWORD_SPECIALS = "!@$%^-_=+[{}]:,./?"
KEEP_CIDRS = {"192.168.0.0/24", "192.168.0.0/16"}


def existing_cluster(clusters, name):
    for cluster in clusters:
        if cluster.get("metadata", {}).get("name") == name:
            return cluster
    return None


def real_outbound_ip():
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for url in ("https://ifconfig.me", "https://ipv4.icanhazip.com", "https://api.myip.com"):
        try:
            with opener.open(url, timeout=8) as resp:
                raw = resp.read().decode().strip()
                if url.endswith("myip.com"):
                    raw = json.loads(raw).get("ip", "")
                if len(raw.split(".")) == 4:
                    return raw
        except Exception:
            pass
    raise RuntimeError("cannot detect direct outbound IPv4")


def list_5443_ingress(ak, sk, project_id, sg_id):
    status, data = hwcloud.api(
        "GET",
        f"https://vpc.{hwcloud.REGION}.myhuaweicloud.com/v1/{project_id}/security-groups/{sg_id}",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"get security group failed: {status} {data}")
    rules = []
    for rule in data.get("security_group", {}).get("security_group_rules", []):
        if (
            rule.get("direction") == "ingress"
            and rule.get("protocol") == "tcp"
            and rule.get("port_range_min") == MASTER_PORT
            and rule.get("port_range_max") == MASTER_PORT
        ):
            rules.append(rule)
    return rules


def add_sg_rule(ak, sk, project_id, sg_id, cidr):
    body = json.dumps({"security_group_rule": {
        "security_group_id": sg_id,
        "direction": "ingress",
        "ethertype": "IPv4",
        "protocol": "tcp",
        "port_range_min": MASTER_PORT,
        "port_range_max": MASTER_PORT,
        "remote_ip_prefix": cidr,
        "description": "dbay-agent kube-apiserver access",
    }})
    status, data = hwcloud.api(
        "POST",
        f"https://vpc.{hwcloud.REGION}.myhuaweicloud.com/v1/{project_id}/security-group-rules",
        ak,
        sk,
        body,
    )
    if status not in (200, 201):
        raise RuntimeError(f"add security group rule failed: {status} {data}")


def delete_sg_rule(ak, sk, project_id, rule_id):
    status, data = hwcloud.api(
        "DELETE",
        f"https://vpc.{hwcloud.REGION}.myhuaweicloud.com/v1/{project_id}/security-group-rules/{rule_id}",
        ak,
        sk,
    )
    if status not in (200, 204):
        raise RuntimeError(f"delete security group rule failed: {status} {data}")


def sync_master_acl(ak, sk, project_id, sg_id):
    cidr = f"{real_outbound_ip()}/32"
    rules = list_5443_ingress(ak, sk, project_id, sg_id)
    if not any(rule.get("remote_ip_prefix") == cidr for rule in rules):
        add_sg_rule(ak, sk, project_id, sg_id, cidr)
    for rule in rules:
        old = rule.get("remote_ip_prefix")
        if old and old not in KEEP_CIDRS and old != cidr:
            delete_sg_rule(ak, sk, project_id, rule["id"])
    print(f"master ACL synced: {sg_id} allows {cidr}")


def wait_available(ak, sk, project_id, cluster_id, timeout_seconds=1800):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        cluster = hwcloud.get_cluster(ak, sk, project_id, cluster_id)
        phase = cluster.get("status", {}).get("phase")
        print(f"cluster phase: {phase}")
        if phase == "Available":
            return cluster
        if phase in {"Error", "Unavailable"}:
            raise RuntimeError(f"cluster entered {phase}")
        time.sleep(20)
    raise TimeoutError(f"cluster {cluster_id} not available within {timeout_seconds}s")


def valid_password(password):
    if not (8 <= len(password) <= 26):
        return False
    classes = [
        any(ch.isupper() for ch in password),
        any(ch.islower() for ch in password),
        any(ch.isdigit() for ch in password),
        any(ch in PASSWORD_SPECIALS for ch in password),
    ]
    allowed = set(string.ascii_letters + string.digits + PASSWORD_SPECIALS)
    return sum(classes) >= 3 and all(ch in allowed for ch in password)


def generated_password():
    alphabet = string.ascii_letters + string.digits + PASSWORD_SPECIALS
    while True:
        password = (
            secrets.choice(string.ascii_uppercase)
            + secrets.choice(string.ascii_lowercase)
            + secrets.choice(string.digits)
            + secrets.choice(PASSWORD_SPECIALS)
            + "".join(secrets.choice(alphabet) for _ in range(12))
        )
        password = "".join(secrets.SystemRandom().sample(password, len(password)))
        if valid_password(password):
            return password


def cluster_body(source_cluster):
    spec = source_cluster["spec"]
    host = spec["hostNetwork"]
    eni = spec["eniNetwork"]
    return {
        "kind": "Cluster",
        "apiVersion": "v3",
        "metadata": {"name": AGENT_CLUSTER_NAME, "alias": AGENT_CLUSTER_NAME},
        "spec": {
            "category": spec.get("category", "Turbo"),
            "type": spec.get("type", "VirtualMachine"),
            "flavor": spec.get("flavor", "cce.s1.small"),
            "version": spec.get("version", "v1.33"),
            "description": "DBay Agent CCE; Lakebase remains in dbay.cloud",
            "ipv6enable": False,
            "billingMode": 0,
            "hostNetwork": {
                "vpc": host["vpc"],
                "subnet": host["subnet"],
                "SecurityGroup": host.get("SecurityGroup"),
            },
            "containerNetwork": {"mode": spec.get("containerNetwork", {}).get("mode", "eni")},
            "eniNetwork": {
                "eniSubnetId": eni["eniSubnetId"],
                "subnets": eni.get("subnets", [{"subnetID": eni["eniSubnetId"]}]),
            },
            "authentication": {"mode": "rbac"},
        },
    }


def nodepool_body(source_node, initial_nodes, max_nodes, password):
    salted = sha512_crypt.using(rounds=5000).hash(password)
    encoded = base64.b64encode(salted.encode()).decode()
    return {
        "kind": "NodePool",
        "apiVersion": "v3",
        "metadata": {"name": AGENT_NODEPOOL_NAME},
        "spec": {
            "initialNodeCount": initial_nodes,
            "type": "vm",
            "autoscaling": {
                "enable": True,
                "minNodeCount": initial_nodes,
                "maxNodeCount": max_nodes,
                "scaleDownCooldownTime": 10,
                "scaleDownUnneededTime": 10,
                "scaleDownUtilizationThreshold": 0.5,
            },
            "nodeTemplate": {
                "flavor": source_node["flavor"],
                "az": source_node["az"],
                "os": source_node["os"],
                "login": {"userPassword": {"password": encoded}},
                "rootVolume": source_node["root_volume"],
                "dataVolumes": [
                    {**volume, "size": max(int(volume.get("size", 100)), 100)}
                    for volume in source_node["data_volumes"]
                ],
                "runtime": source_node.get("runtime", {"name": "containerd"}),
                "extendParam": {"maxPods": 110},
                "nodeNicSpec": {"primaryNic": {"subnetId": source_node["subnet_id"]}},
                "k8sTags": {"dbay/pool": "agent"},
            },
        },
    }


def ensure_master_eip(ak, sk, project_id, cluster_id):
    cluster = hwcloud.get_cluster(ak, sk, project_id, cluster_id)
    for endpoint in cluster.get("status", {}).get("endpoints", []):
        if endpoint.get("type") == "External":
            print(f"master EIP already bound: {endpoint.get('url')}")
            return endpoint.get("url")
    status, data = hwcloud.create_eip(ak, sk, project_id, {
        "publicip": {"type": "5_sbgp"},
        "bandwidth": {
            "name": "dbay-agent-cce-api-bw",
            "size": 5,
            "share_type": "PER",
            "charge_mode": "traffic",
        },
    })
    if status not in (200, 201):
        raise RuntimeError(f"create master EIP failed: {status} {data}")
    eip_id = data.get("publicip", data)["id"]
    status, data = hwcloud.api(
        "PUT",
        f"https://cce.{hwcloud.REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters/{cluster_id}/mastereip",
        ak,
        sk,
        json.dumps({"spec": {"action": "bind", "spec": {"id": eip_id}}}),
        timeout=60,
    )
    if status not in (200, 201, 202):
        raise RuntimeError(f"bind master EIP failed: {status} {data}")
    print(f"master EIP bind submitted: {eip_id}")
    return eip_id


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--initial-nodes", type=int, default=1)
    parser.add_argument("--max-nodes", type=int, default=2)
    parser.add_argument("--bind-master-eip", action="store_true")
    args = parser.parse_args()

    ak, sk, creds = hwcloud.load_credentials()
    if not ak or not sk:
        raise SystemExit("HWCLOUD_AK/HWCLOUD_SK are required")
    password = (
        os.environ.get("CCE_AGENT_NODE_PASSWORD")
        or os.environ.get("CCE_NODE_PASSWORD")
        or creds.get("CCE_AGENT_NODE_PASSWORD")
        or creds.get("CCE_NODE_PASSWORD")
    )
    if not password or not valid_password(password):
        password = generated_password()
        print("generated a compliant node password for this run")

    project_id = hwcloud.get_project_id(ak, sk)
    clusters = hwcloud.list_clusters(ak, sk, project_id)
    source = existing_cluster(clusters, SOURCE_CLUSTER_NAME)
    if not source:
        raise RuntimeError(f"source cluster not found: {SOURCE_CLUSTER_NAME}")
    source_detail = hwcloud.get_cluster(ak, sk, project_id, source["metadata"]["uid"])
    source_nodes = [
        node for node in hwcloud.get_nodes(ak, sk, project_id, source["metadata"]["uid"])
        if node["flavor"] and node["subnet_id"]
    ]
    if not source_nodes:
        raise RuntimeError("no source node found to copy")

    c_body = cluster_body(source_detail)
    n_body = nodepool_body(source_nodes[0], args.initial_nodes, args.max_nodes, password)
    redacted = json.loads(json.dumps(n_body))
    redacted["spec"]["nodeTemplate"]["login"]["userPassword"]["password"] = "***"
    print(json.dumps({"cluster": c_body, "nodepool": redacted}, ensure_ascii=False, indent=2))
    if not args.execute:
        print("Plan only. Re-run with --execute.")
        return

    cluster = existing_cluster(clusters, AGENT_CLUSTER_NAME)
    if cluster:
        cluster_id = cluster["metadata"]["uid"]
        print(f"cluster already exists: {AGENT_CLUSTER_NAME} {cluster_id}")
    else:
        status, data = hwcloud.api(
            "POST",
            f"https://cce.{hwcloud.REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters",
            ak,
            sk,
            json.dumps(c_body),
            timeout=60,
        )
        if status not in (200, 201, 202):
            raise RuntimeError(f"create cluster failed: {status} {data}")
        cluster_id = data.get("metadata", {}).get("uid") or data.get("uid")
        print(f"cluster create submitted: {cluster_id}")

    cluster_detail = wait_available(ak, sk, project_id, cluster_id)
    control_sg = cluster_detail["spec"]["hostNetwork"]["controlPlaneSecurityGroup"]
    sync_master_acl(ak, sk, project_id, control_sg)

    status, pools = hwcloud.api(
        "GET",
        f"https://cce.{hwcloud.REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters/{cluster_id}/nodepools",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"list nodepools failed: {status} {pools}")
    if any(pool.get("metadata", {}).get("name") == AGENT_NODEPOOL_NAME for pool in pools.get("items", [])):
        print(f"nodepool already exists: {AGENT_NODEPOOL_NAME}")
    else:
        status, data = hwcloud.api(
            "POST",
            f"https://cce.{hwcloud.REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters/{cluster_id}/nodepools",
            ak,
            sk,
            json.dumps(n_body),
            timeout=60,
        )
        if status not in (200, 201, 202):
            raise RuntimeError(f"create nodepool failed: {status} {data}")
        print(f"nodepool create submitted: {data.get('metadata', {}).get('uid', '')}")

    if args.bind_master_eip:
        ensure_master_eip(ak, sk, project_id, cluster_id)


if __name__ == "__main__":
    main()
