#!/usr/bin/env python3
"""Provision a dedicated PostgreSQL RDS instance for DBay Agent."""

import argparse
import json
import os
import secrets
import string
import time

import hwcloud

AGENT_RDS_NAME = "dbay-agent-rds"
AGENT_RDS_SUBNET_NAME = "dbay-agent-rds-subnet"
AGENT_RDS_SUBNET_CIDR = "192.168.2.0/24"
AGENT_RDS_SUBNET_GATEWAY = "192.168.2.1"
SOURCE_RDS_NAME_PREFIX = "dbay-rds"
ENV_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
PASSWORD_SPECIALS = "~!@#%^*-_=+?"


def generated_password():
    alphabet = string.ascii_letters + string.digits + PASSWORD_SPECIALS
    while True:
        password = (
            secrets.choice(string.ascii_uppercase)
            + secrets.choice(string.ascii_lowercase)
            + secrets.choice(string.digits)
            + secrets.choice(PASSWORD_SPECIALS)
            + "".join(secrets.choice(alphabet) for _ in range(14))
        )
        password = "".join(secrets.SystemRandom().sample(password, len(password)))
        if valid_password(password):
            return password


def valid_password(password):
    if not (8 <= len(password) <= 32):
        return False
    classes = [
        any(ch.isupper() for ch in password),
        any(ch.islower() for ch in password),
        any(ch.isdigit() for ch in password),
        any(ch in PASSWORD_SPECIALS for ch in password),
    ]
    allowed = set(string.ascii_letters + string.digits + PASSWORD_SPECIALS)
    return sum(classes) >= 3 and all(ch in allowed for ch in password)


def list_rds(ak, sk, project_id, name=None):
    query = f"?name={name}" if name else ""
    status, data = hwcloud.api(
        "GET",
        f"https://rds.{hwcloud.REGION}.myhuaweicloud.com/v3/{project_id}/instances{query}",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"list RDS failed: {status} {data}")
    return data.get("instances", [])


def source_rds(instances):
    postgres = [
        inst for inst in instances
        if inst.get("datastore", {}).get("type") == "PostgreSQL"
        and inst.get("name", "").startswith(SOURCE_RDS_NAME_PREFIX)
    ]
    if not postgres:
        raise RuntimeError("source Lakebase PostgreSQL RDS not found")
    return postgres[0]


def get_subnet(ak, sk, project_id, subnet_id):
    status, data = hwcloud.api(
        "GET",
        f"https://vpc.{hwcloud.REGION}.myhuaweicloud.com/v1/{project_id}/subnets/{subnet_id}",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"get subnet failed: {status} {data}")
    return data["subnet"]


def list_subnets(ak, sk, project_id, vpc_id):
    status, data = hwcloud.api(
        "GET",
        f"https://vpc.{hwcloud.REGION}.myhuaweicloud.com/v1/{project_id}/subnets?vpc_id={vpc_id}",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"list subnets failed: {status} {data}")
    return data.get("subnets", [])


def ensure_rds_subnet(ak, sk, project_id, source, execute):
    source_subnet = get_subnet(ak, sk, project_id, source["subnet_id"])
    if int(source_subnet.get("available_ip_address_count") or 0) >= 8:
        return source["subnet_id"]
    vpc_id = source["vpc_id"]
    target_name = os.environ.get("DBAY_AGENT_RDS_SUBNET_NAME", AGENT_RDS_SUBNET_NAME)
    for subnet in list_subnets(ak, sk, project_id, vpc_id):
        if subnet.get("name") == target_name:
            print(f"using existing RDS subnet: {target_name} {subnet['id']}")
            return subnet["id"]

    cidr = os.environ.get("DBAY_AGENT_RDS_SUBNET_CIDR", AGENT_RDS_SUBNET_CIDR)
    gateway = os.environ.get("DBAY_AGENT_RDS_SUBNET_GATEWAY", AGENT_RDS_SUBNET_GATEWAY)
    body = {
        "subnet": {
            "name": target_name,
            "cidr": cidr,
            "gateway_ip": gateway,
            "vpc_id": vpc_id,
            "dhcp_enable": True,
            "primary_dns": source_subnet.get("primary_dns", "100.125.1.250"),
            "secondary_dns": source_subnet.get("secondary_dns", "100.125.129.250"),
        }
    }
    print(json.dumps({"create_subnet": body}, ensure_ascii=False, indent=2))
    if not execute:
        return f"<new subnet {target_name}>"
    status, data = hwcloud.api(
        "POST",
        f"https://vpc.{hwcloud.REGION}.myhuaweicloud.com/v1/{project_id}/subnets",
        ak,
        sk,
        json.dumps(body),
        timeout=60,
    )
    if status not in (200, 201, 202):
        raise RuntimeError(f"create subnet failed: {status} {data}")
    subnet = data.get("subnet", data)
    print(f"subnet create submitted: {subnet.get('id')}")
    return subnet["id"]


def create_body(source, password, name, subnet_id):
    nodes = source.get("nodes") or []
    availability_zone = nodes[0].get("availability_zone") if nodes else "cn-north-4a"
    datastore = source.get("datastore", {})
    return {
        "name": name,
        "datastore": {
            "type": "PostgreSQL",
            "version": datastore.get("version", "17"),
        },
        "flavor_ref": source["flavor_ref"],
        "volume": {
            "type": source.get("volume", {}).get("type", "CLOUDSSD"),
            "size": source.get("volume", {}).get("size", 40),
        },
        "region": hwcloud.REGION,
        "availability_zone": availability_zone,
        "vpc_id": source["vpc_id"],
        "subnet_id": subnet_id,
        "security_group_id": source["security_group_id"],
        "password": password,
        "port": "5432",
        "charge_info": {"charge_mode": "postPaid"},
        "backup_strategy": {
            "start_time": "18:00-19:00",
            "keep_days": 7,
        },
        "enterprise_project_id": source.get("enterprise_project_id", "0"),
    }


def wait_active(ak, sk, project_id, instance_id, timeout_seconds=3600):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        matches = [inst for inst in list_rds(ak, sk, project_id) if inst.get("id") == instance_id]
        if not matches:
            raise RuntimeError(f"RDS instance disappeared: {instance_id}")
        inst = matches[0]
        status = inst.get("status")
        print(f"RDS status: {status}")
        if status == "ACTIVE" and inst.get("private_ips"):
            return inst
        if status in {"ERROR", "FAILED"}:
            raise RuntimeError(f"RDS entered {status}: {inst}")
        time.sleep(30)
    raise TimeoutError(f"RDS {instance_id} not ACTIVE within {timeout_seconds}s")


def upsert_env(values):
    current = {}
    if os.path.exists(ENV_PATH):
        with open(ENV_PATH, encoding="utf-8") as f:
            for line in f:
                stripped = line.strip()
                if not stripped or stripped.startswith("#") or "=" not in stripped:
                    continue
                key, value = stripped.removeprefix("export ").split("=", 1)
                current[key.strip()] = value.strip().strip("'\"")
    current.update(values)
    lines = [f'{key}="{value}"\n' for key, value in sorted(current.items())]
    with open(ENV_PATH, "w", encoding="utf-8") as f:
        f.writelines(lines)
    os.chmod(ENV_PATH, 0o600)
    print(f"updated local env: {ENV_PATH}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--name", default=AGENT_RDS_NAME)
    args = parser.parse_args()

    agent_rds_name = args.name

    ak, sk, creds = hwcloud.load_credentials()
    if not ak or not sk:
        raise SystemExit("HWCLOUD_AK/HWCLOUD_SK are required")
    project_id = hwcloud.get_project_id(ak, sk)
    existing = list_rds(ak, sk, project_id, agent_rds_name)
    if existing:
        inst = existing[0]
        print(json.dumps({
            "id": inst.get("id"),
            "name": inst.get("name"),
            "status": inst.get("status"),
            "private_ips": inst.get("private_ips"),
            "flavor_ref": inst.get("flavor_ref"),
            "datastore": inst.get("datastore"),
        }, ensure_ascii=False, indent=2))
        if args.execute and inst.get("private_ips"):
            upsert_env({
                "DBAY_AGENT_RDS_INSTANCE_ID": inst["id"],
                "DBAY_AGENT_RDS_PRIVATE_IP": inst["private_ips"][0],
            })
        return

    source = source_rds(list_rds(ak, sk, project_id))
    subnet_id = ensure_rds_subnet(ak, sk, project_id, source, args.execute)
    root_password = os.environ.get("DBAY_AGENT_RDS_ROOT_PASSWORD") or creds.get("DBAY_AGENT_RDS_ROOT_PASSWORD") or generated_password()
    if not valid_password(root_password):
        raise SystemExit("DBAY_AGENT_RDS_ROOT_PASSWORD does not satisfy Huawei RDS password rules")
    body = create_body(source, root_password, agent_rds_name, subnet_id)
    redacted = json.loads(json.dumps(body))
    redacted["password"] = "***"
    print(json.dumps(redacted, ensure_ascii=False, indent=2))
    if not args.execute:
        print("Plan only. Re-run with --execute.")
        return

    status, data = hwcloud.api(
        "POST",
        f"https://rds.{hwcloud.REGION}.myhuaweicloud.com/v3/{project_id}/instances",
        ak,
        sk,
        json.dumps(body),
        timeout=60,
    )
    if status not in (200, 201, 202):
        raise RuntimeError(f"create RDS failed: {status} {data}")
    instance = data.get("instance") or data
    instance_id = instance.get("id")
    if not instance_id:
        raise RuntimeError(f"create RDS returned no instance id: {data}")
    print(f"RDS create submitted: {instance_id}")
    active = wait_active(ak, sk, project_id, instance_id)
    upsert_env({
        "DBAY_AGENT_RDS_INSTANCE_ID": active["id"],
        "DBAY_AGENT_RDS_PRIVATE_IP": active["private_ips"][0],
        "DBAY_AGENT_RDS_ROOT_USER": active.get("db_user_name", "root"),
        "DBAY_AGENT_RDS_ROOT_PASSWORD": root_password,
    })
    print(json.dumps({
        "id": active["id"],
        "name": active["name"],
        "status": active["status"],
        "private_ip": active["private_ips"][0],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
