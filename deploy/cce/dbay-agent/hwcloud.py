#!/usr/bin/env python3
import hashlib
import hmac
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

REGION = os.environ.get("HWCLOUD_REGION", "cn-north-4")
ENV_FILE = os.environ.get(
    "DBAY_AGENT_ENV_FILE",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"),
)


def load_credentials():
    creds = {}
    env_files = [
        ENV_FILE,
        "/Users/jacky/code/lakeon/deploy/cce/sites/hwstaff/.env",
    ]
    for path in env_files:
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if "=" not in line or line.startswith("#"):
                    continue
                line = line.removeprefix("export ").strip()
                key, val = line.split("=", 1)
                creds.setdefault(key.strip(), val.strip().strip("'\""))
    ak = os.environ.get("HWCLOUD_AK") or creds.get("HWCLOUD_AK") or creds.get("OBS_AK")
    sk = os.environ.get("HWCLOUD_SK") or creds.get("HWCLOUD_SK") or creds.get("OBS_SK")
    return ak, sk, creds


def _hmac256(key, msg):
    if isinstance(key, str):
        key = key.encode()
    if isinstance(msg, str):
        msg = msg.encode()
    return hmac.new(key, msg, hashlib.sha256).digest()


def _sha256(data):
    if isinstance(data, str):
        data = data.encode()
    return hashlib.sha256(data).hexdigest()


def api(method, url, ak, sk, body="", timeout=30, add_trailing_slash=True):
    parsed = urllib.parse.urlparse(url)
    host = parsed.hostname
    path = parsed.path or "/"
    if add_trailing_slash and not path.endswith("/"):
        path += "/"
    query = parsed.query or ""
    if query:
        params = urllib.parse.parse_qsl(query, keep_blank_values=True)
        params.sort(key=lambda x: x[0])
        query = urllib.parse.urlencode(params)

    now = datetime.now(timezone.utc)
    ts = now.strftime("%Y%m%dT%H%M%SZ")
    ds = now.strftime("%Y%m%d")
    base = host.replace(".myhuaweicloud.com", "")
    parts = base.split(".")
    svc, rgn = (parts[0], parts[1]) if len(parts) >= 2 else (parts[0], REGION)

    signed_headers = "host;x-sdk-date"
    canonical_headers = f"host:{host}\nx-sdk-date:{ts}\n"
    canonical_request = f"{method}\n{path}\n{query}\n{canonical_headers}\n{signed_headers}\n{_sha256(body)}"
    scope = f"{ds}/{rgn}/{svc}/sdk_request"
    string_to_sign = f"SDK-HMAC-SHA256\n{ts}\n{scope}\n{_sha256(canonical_request)}"
    key = _hmac256(_hmac256(_hmac256(_hmac256(f"SDK{sk}".encode(), ds), rgn), svc), "sdk_request")
    signature = hmac.new(key, string_to_sign.encode(), hashlib.sha256).hexdigest()

    headers = {
        "Host": host,
        "X-Sdk-Date": ts,
        "Authorization": (
            f"SDK-HMAC-SHA256 Credential={ak}/{scope}, "
            f"SignedHeaders={signed_headers}, Signature={signature}"
        ),
    }
    if body:
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(
        url,
        data=body.encode() if body else None,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"error": str(e)}
    except Exception as e:
        return 0, {"error": str(e)}


def get_project_id(ak, sk):
    status, data = api("GET", f"https://iam.myhuaweicloud.com/v3/projects?name={REGION}", ak, sk)
    if status == 200 and data.get("projects"):
        return data["projects"][0]["id"]
    raise RuntimeError(f"get project id failed: {status} {data}")


def list_clusters(ak, sk, project_id):
    status, data = api(
        "GET",
        f"https://cce.{REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"list clusters failed: {status} {data}")
    return data.get("items", [])


def get_cluster(ak, sk, project_id, cluster_id):
    status, data = api(
        "GET",
        f"https://cce.{REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters/{cluster_id}",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"get cluster failed: {status} {data}")
    return data


def get_nodes(ak, sk, project_id, cluster_id):
    status, data = api(
        "GET",
        f"https://cce.{REGION}.myhuaweicloud.com/api/v3/projects/{project_id}/clusters/{cluster_id}/nodes",
        ak,
        sk,
    )
    if status != 200:
        raise RuntimeError(f"list nodes failed: {status} {data}")
    nodes = []
    for node in data.get("items", []):
        spec = node.get("spec", {})
        nodes.append({
            "uid": node.get("metadata", {}).get("uid"),
            "name": node.get("metadata", {}).get("name"),
            "flavor": spec.get("flavor"),
            "az": spec.get("az"),
            "os": spec.get("os", "Huawei Cloud EulerOS 2.0"),
            "root_volume": spec.get("rootVolume", {"volumetype": "GPSSD", "size": 50}),
            "data_volumes": spec.get("dataVolumes", [{"volumetype": "GPSSD", "size": 100}]),
            "runtime": spec.get("runtime", {"name": "containerd"}),
            "subnet_id": spec.get("nodeNicSpec", {}).get("primaryNic", {}).get("subnetId", ""),
            "phase": node.get("status", {}).get("phase"),
        })
    return nodes


def create_eip(ak, sk, project_id, config):
    return api(
        "POST",
        f"https://vpc.{REGION}.myhuaweicloud.com/v1/{project_id}/publicips",
        ak,
        sk,
        json.dumps(config),
    )
