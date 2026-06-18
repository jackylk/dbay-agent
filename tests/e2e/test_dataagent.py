from conftest import assert_ok


def test_dataagent_runtime_state_flow(client, agent_base_url, tenant_headers):
    app = assert_ok(client.post(
        f"{agent_base_url}/agent-state/apps",
        headers=tenant_headers,
        json={"key": "e2e-data-agent", "display_name": "E2E DataAgent", "type": "data"},
    ))
    task = assert_ok(client.post(
        f"{agent_base_url}/agent-state/apps/{app['id']}/runs",
        headers=tenant_headers,
        json={"goal": "Analyze source data"},
    ))
    workspace = assert_ok(client.post(
        f"{agent_base_url}/agent-state/workspaces",
        headers=tenant_headers,
        json={"task_run_id": task["id"], "name": "analysis"},
    ))
    evidence = assert_ok(client.post(
        f"{agent_base_url}/agent-state/evidence-packets",
        headers=tenant_headers,
        json={"task_run_id": task["id"], "summary": "e2e evidence verified"},
    ))
    policy = assert_ok(client.post(
        f"{agent_base_url}/agent-state/policy/check",
        headers=tenant_headers,
        json={"task_run_id": task["id"], "action": "read_dataset"},
    ))
    detail = assert_ok(client.get(
        f"{agent_base_url}/agent-state/task-runs/{task['id']}",
        headers=tenant_headers,
    ))

    assert task["agent_app_id"] == app["id"]
    assert workspace["task_run_id"] == task["id"]
    assert evidence["task_run_id"] == task["id"]
    assert policy["decision"] == "ALLOW"
    assert detail["workspace"]["id"] == workspace["id"]
    assert detail["evidence_packets"][0]["id"] == evidence["id"]
