from conftest import assert_ok


def test_notebook_session_uses_dbay_agent_cci_boundary(client, agent_base_url, tenant_headers):
    session = assert_ok(client.post(
        f"{agent_base_url}/notebooks/sessions",
        headers=tenant_headers,
        json={"name": "e2e notebook", "image": "jupyter/base-notebook"},
    ))
    fetched = assert_ok(client.get(
        f"{agent_base_url}/notebooks/sessions/{session['id']}",
        headers=tenant_headers,
    ))
    stopped = assert_ok(client.delete(
        f"{agent_base_url}/notebooks/sessions/{session['id']}",
        headers=tenant_headers,
    ))

    assert session["kind"] == "NOTEBOOK"
    assert fetched["placement"]["cluster_owner"] == "dbay-agent"
    assert fetched["placement"]["namespace"] == "dbay-agent-workers"
    assert fetched["placement"]["backend"] == "CCI"
    assert fetched["placement"]["runs_in_lakebase_cluster"] is False
    assert stopped["status"] == "CANCELLED"
