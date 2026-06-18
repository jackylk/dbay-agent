from conftest import assert_ok


def test_memory_ingest_recall_raw_messages_and_stats(client, agent_base_url, tenant_headers):
    base = assert_ok(client.post(
        f"{agent_base_url}/memory/bases",
        headers=tenant_headers,
        json={"name": "E2E Memory", "scene": "DEVELOPER_TOOL"},
    ))
    item = assert_ok(client.post(
        f"{agent_base_url}/memory/bases/{base['id']}/ingest",
        headers=tenant_headers,
        json={
            "content": "DBay Agent active runtime validates memory recall.",
            "memory_type": "preference",
            "source": "e2e",
        },
    ))
    recall = assert_ok(client.post(
        f"{agent_base_url}/memory/bases/{base['id']}/recall",
        headers=tenant_headers,
        json={"query": "memory recall"},
    ))
    raw = assert_ok(client.get(
        f"{agent_base_url}/memory/bases/{base['id']}/raw_messages",
        headers=tenant_headers,
    ))
    stats = assert_ok(client.get(
        f"{agent_base_url}/memory/bases/{base['id']}/stats",
        headers=tenant_headers,
    ))

    assert item["memory_base_id"] == base["id"]
    assert recall["total"] >= 1
    assert raw["total"] >= 1
    assert stats["memory_count"] >= 1
