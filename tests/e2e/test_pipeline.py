from conftest import assert_ok


def test_pipeline_version_run_and_cancel(client, agent_base_url, tenant_headers):
    pipeline = assert_ok(client.post(
        f"{agent_base_url}/pipelines",
        headers=tenant_headers,
        json={"name": "e2e pipeline", "data_type": "TEXT", "dag_yaml": "name: e2e"},
    ))
    version = assert_ok(client.post(
        f"{agent_base_url}/pipelines/{pipeline['id']}/versions",
        headers=tenant_headers,
        json={"dag_yaml": "name: e2e-v2", "changelog": "v2"},
    ))
    run = assert_ok(client.post(
        f"{agent_base_url}/pipeline-runs",
        headers=tenant_headers,
        json={"pipeline_id": pipeline["id"], "version": 1, "parameters": {"sample": True}},
    ))
    cancelled = assert_ok(client.post(
        f"{agent_base_url}/pipeline-runs/{run['id']}/cancel",
        headers=tenant_headers,
    ))

    assert pipeline["latest_version"] == 1
    assert version["version"] == 2
    assert run["pipeline_id"] == pipeline["id"]
    assert cancelled["status"] == "CANCELLED"
