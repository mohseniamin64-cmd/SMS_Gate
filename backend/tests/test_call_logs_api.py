from __future__ import annotations

from pathlib import Path

import pytest

from backend.app import create_app


@pytest.fixture()
def client(tmp_path: Path):
    app = create_app({
        "TESTING": True,
        "DATABASE": str(tmp_path / "call_logs.db"),
        "API_KEY": "test-key",
    })
    return app.test_client()


def headers() -> dict[str, str]:
    return {"Authorization": "Bearer test-key"}


def call_payload(call_id: str = "call-1", call_type: str = "INCOMING") -> dict:
    return {
        "id": call_id,
        "number": "+989111111111",
        "timestamp": 1_735_000_000_000,
        "durationSeconds": 42,
        "type": call_type,
        "contactName": None,
    }


def test_health_requires_bearer_key(client) -> None:
    assert client.get("/api/health").status_code == 401
    response = client.get("/api/health", headers=headers())
    assert response.status_code == 200
    assert response.get_json()["service"] == "sms-center"


def test_sync_is_idempotent_and_keeps_contacts_local(client) -> None:
    endpoint = "/api/call-logs/sync"
    first = client.post(endpoint, json={"deviceId": "device-a", "calls": [call_payload()]}, headers=headers())
    assert first.status_code == 200
    assert first.get_json()["accepted"] == ["call-1"]

    second = client.post(endpoint, json={"deviceId": "device-a", "calls": [call_payload()]}, headers=headers())
    assert second.status_code == 200
    assert second.get_json()["duplicates"] == ["call-1"]

    listed = client.get("/api/call-logs?deviceId=device-a", headers=headers()).get_json()
    assert listed["total"] == 1
    assert listed["calls"][0]["contactName"] is None


def test_sync_rejects_contact_name_and_invalid_duration(client) -> None:
    invalid = call_payload("bad")
    invalid.update(contactName="Local name", durationSeconds=-1)
    response = client.post(
        "/api/call-logs/sync",
        json={"deviceId": "device-a", "calls": [invalid]},
        headers=headers(),
    )
    assert response.status_code == 200
    rejected = response.get_json()["rejected"]
    assert rejected[0]["id"] == "bad"
    assert "durationSeconds" in rejected[0]["reason"]


def test_list_filters_type_and_isolated_by_device(client) -> None:
    response = client.post(
        "/api/call-logs/sync",
        json={"deviceId": "device-a", "calls": [call_payload("in"), call_payload("missed", "MISSED")]},
        headers=headers(),
    )
    assert response.status_code == 200
    listed = client.get("/api/call-logs?deviceId=device-a&type=MISSED&limit=1", headers=headers())
    assert listed.status_code == 200
    assert listed.get_json()["calls"][0]["type"] == "MISSED"
    other = client.get("/api/call-logs?deviceId=device-b", headers=headers())
    assert other.get_json()["total"] == 0


def test_sync_enforces_batch_limit(client) -> None:
    response = client.post(
        "/api/call-logs/sync",
        json={"deviceId": "device-a", "calls": [call_payload(str(i)) for i in range(501)]},
        headers=headers(),
    )
    assert response.status_code == 400
    assert "batch" in response.get_json()["error"]
