from conftest import assert_ok


def test_knowledge_ingest_search_and_wiki(client, agent_base_url, tenant_headers):
    kb = assert_ok(client.post(
        f"{agent_base_url}/knowledge/bases",
        headers=tenant_headers,
        json={"name": "E2E Knowledge", "description": "dbay-agent owned"},
    ))
    doc = assert_ok(client.post(
        f"{agent_base_url}/knowledge/ingest",
        headers=tenant_headers,
        json={
            "kb_id": kb["id"],
            "title": "Agent runtime",
            "content": "DBay Agent owns knowledge chunks and wiki pages in its own RDS.",
            "tags": ["e2e"],
        },
    ))
    docs = assert_ok(client.get(
        f"{agent_base_url}/knowledge/documents",
        headers=tenant_headers,
        params={"kb_id": kb["id"]},
    ))
    chunks = assert_ok(client.get(
        f"{agent_base_url}/knowledge/bases/{kb['id']}/documents/{doc['id']}/chunks",
        headers=tenant_headers,
    ))
    search = assert_ok(client.post(
        f"{agent_base_url}/knowledge/search",
        headers=tenant_headers,
        json={"kb_id": kb["id"], "query": "wiki pages"},
    ))
    wiki = assert_ok(client.get(
        f"{agent_base_url}/knowledge/wiki/pages",
        headers=tenant_headers,
        params={"kb_id": kb["id"]},
    ))

    assert docs[0]["id"] == doc["id"]
    assert chunks["chunks"][0]["content"]
    assert search["total"] >= 1
    assert len(wiki) >= 1
