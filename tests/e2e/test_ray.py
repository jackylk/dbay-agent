from conftest import assert_ok


def test_ray_job_uses_dbay_agent_cci_boundary(client, agent_base_url, tenant_headers):
    placement = assert_ok(client.get(f"{agent_base_url}/workloads/placement"))
    assert placement["cluster_owner"] == "dbay-agent"
    assert placement["namespace"] == "dbay-agent-workers"
    assert placement["backend"] == "CCI"
    assert placement["runs_in_lakebase_cluster"] is False

    job = assert_ok(client.post(
        f"{agent_base_url}/ray/jobs",
        headers=tenant_headers,
        json={"name": "e2e ray", "entrypoint": "python main.py"},
    ))
    fetched = assert_ok(client.get(
        f"{agent_base_url}/ray/jobs/{job['id']}",
        headers=tenant_headers,
    ))
    cancelled = assert_ok(client.post(
        f"{agent_base_url}/ray/jobs/{job['id']}/cancel",
        headers=tenant_headers,
    ))

    assert job["kind"] == "RAY"
    assert fetched["placement"]["cluster_owner"] == "dbay-agent"
    assert fetched["placement"]["backend"] == "CCI"
    assert fetched["placement"]["runs_in_lakebase_cluster"] is False
    assert cancelled["status"] == "CANCELLED"
