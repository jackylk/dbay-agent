import os
import time

import httpx
import pytest


BASE_URL = os.environ.get("DBAY_AGENT_ENDPOINT", "https://dbay-agent.up.railway.app/agent-api").rstrip("/")
HTTP_TIMEOUT = float(os.environ.get("DBAY_AGENT_E2E_TIMEOUT", "30"))


@pytest.fixture(scope="session")
def agent_base_url():
    return f"{BASE_URL}/api/v1"


@pytest.fixture
def tenant_headers():
    return {
        "X-DBay-Tenant-Id": f"dbay-agent-e2e-{int(time.time() * 1000)}",
        "Content-Type": "application/json",
    }


@pytest.fixture
def client():
    with httpx.Client(timeout=HTTP_TIMEOUT, verify=False) as c:
        yield c


def assert_ok(response):
    assert response.status_code < 400, response.text
    return response.json()
