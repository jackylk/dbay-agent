from conftest import assert_ok


def test_datalake_dataset_and_job_flow(client, agent_base_url, tenant_headers):
    dataset = assert_ok(client.post(
        f"{agent_base_url}/datalake/datasets",
        headers=tenant_headers,
        json={"name": "events", "source_type": "ICEBERG"},
    ))
    job = assert_ok(client.post(
        f"{agent_base_url}/datalake/jobs",
        headers=tenant_headers,
        json={"name": "ray batch", "type": "RAY", "dataset_id": dataset["id"]},
    ))
    jobs = assert_ok(client.get(f"{agent_base_url}/datalake/jobs", headers=tenant_headers))
    cancelled = assert_ok(client.delete(
        f"{agent_base_url}/datalake/jobs/{job['id']}",
        headers=tenant_headers,
    ))

    assert dataset["source_type"] == "ICEBERG"
    assert any(existing["id"] == job["id"] for existing in jobs)
    assert cancelled["status"] == "CANCELLED"
