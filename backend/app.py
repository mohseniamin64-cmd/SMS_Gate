"""Authenticated Call Log API for SMS Center.

This module owns only the call-log store. Existing SMS routes can continue to
be served by the legacy panel without sharing this table or migration.
"""

from __future__ import annotations

import hmac
import os
import sqlite3
from functools import wraps
from pathlib import Path
from typing import Any, Callable

from flask import Flask, current_app, g, jsonify, request

CALL_TYPES = {"INCOMING", "OUTGOING", "MISSED"}
MAX_BATCH_SIZE = 500
MAX_TEXT_LENGTH = 256


def create_app(test_config: dict[str, Any] | None = None) -> Flask:
    app = Flask(__name__)
    default_db = Path(__file__).with_name("call_logs.db")
    app.config.from_mapping(
        DATABASE=os.environ.get("CALL_LOG_DB", str(default_db)),
        API_KEY=os.environ.get("SMS_CENTER_API_KEY", ""),
    )
    if test_config:
        app.config.update(test_config)

    with app.app_context():
        _init_db()

    @app.get("/api/health")
    @requires_auth
    def health():
        return jsonify(success=True, service="sms-center")

    @app.post("/api/call-logs/sync")
    @requires_auth
    def sync_call_logs():
        payload = request.get_json(silent=True)
        if not isinstance(payload, dict):
            return _error("JSON object is required", 400)
        device_id = payload.get("deviceId")
        calls = payload.get("calls")
        if not _valid_text(device_id, MAX_TEXT_LENGTH):
            return _error("deviceId is required", 400)
        if not isinstance(calls, list):
            return _error("calls must be an array", 400)
        if len(calls) > MAX_BATCH_SIZE:
            return _error("calls exceeds the batch limit", 400)

        accepted: list[str] = []
        duplicates: list[str] = []
        rejected: list[dict[str, str | None]] = []
        valid_calls: list[dict[str, Any]] = []
        seen: set[str] = set()
        for raw_call in calls:
            call_id = raw_call.get("id") if isinstance(raw_call, dict) else None
            if not _valid_text(call_id, MAX_TEXT_LENGTH):
                rejected.append({
                    "id": call_id if isinstance(call_id, str) else None,
                    "reason": "id is required",
                })
                continue
            if call_id in seen:
                duplicates.append(call_id)
                continue
            seen.add(call_id)
            reason = _validate_call(raw_call)
            if reason:
                rejected.append({"id": call_id, "reason": reason})
                continue
            valid_calls.append(raw_call)

        db = get_db()
        try:
            for call in valid_calls:
                cursor = db.execute(
                    """
                    INSERT OR IGNORE INTO call_logs
                    (device_id, call_id, number, timestamp, duration_seconds, type, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, strftime('%s','now') * 1000)
                    """,
                    (
                        device_id,
                        call["id"],
                        call["number"].strip(),
                        call["timestamp"],
                        call["durationSeconds"],
                        call["type"].upper(),
                    ),
                )
                if cursor.rowcount == 1:
                    accepted.append(call["id"])
                else:
                    duplicates.append(call["id"])
            db.commit()
        except sqlite3.Error:
            db.rollback()
            return _error("call-log batch could not be stored", 500)

        return jsonify(
            success=True,
            accepted=accepted,
            duplicates=duplicates,
            rejected=rejected,
        )

    @app.get("/api/call-logs")
    @requires_auth
    def list_call_logs():
        device_id = request.args.get("deviceId", "")
        if not _valid_text(device_id, MAX_TEXT_LENGTH):
            return _error("deviceId is required", 400)
        call_type = request.args.get("type")
        if call_type is not None:
            call_type = call_type.upper()
            if call_type not in CALL_TYPES:
                return _error("type must be INCOMING, OUTGOING, or MISSED", 400)
        try:
            limit = int(request.args.get("limit", "100"))
            offset = int(request.args.get("offset", "0"))
        except ValueError:
            return _error("limit and offset must be integers", 400)
        if not 1 <= limit <= 500 or offset < 0:
            return _error("limit must be 1..500 and offset must be non-negative", 400)

        where = ["device_id = ?"]
        params: list[Any] = [device_id]
        if call_type:
            where.append("type = ?")
            params.append(call_type)
        where_sql = " AND ".join(where)
        db = get_db()
        total = db.execute(
            "SELECT COUNT(*) FROM call_logs WHERE " + where_sql,
            params,
        ).fetchone()[0]
        rows = db.execute(
            """
            SELECT call_id, number, timestamp, duration_seconds, type
            FROM call_logs
            WHERE """
            + where_sql
            + " ORDER BY timestamp DESC, call_id DESC LIMIT ? OFFSET ?",
            params + [limit, offset],
        ).fetchall()
        calls = [
            {
                "id": row["call_id"],
                "number": row["number"],
                "contactName": None,
                "timestamp": row["timestamp"],
                "durationSeconds": row["duration_seconds"],
                "type": row["type"],
            }
            for row in rows
        ]
        return jsonify(success=True, calls=calls, total=total, limit=limit, offset=offset)

    @app.teardown_appcontext
    def close_connection(_exception: BaseException | None):
        db = g.pop("db", None)
        if db is not None:
            db.close()

    return app


def requires_auth(view: Callable[..., Any]) -> Callable[..., Any]:
    @wraps(view)
    def wrapped(*args: Any, **kwargs: Any):
        expected = str(current_app.config.get("API_KEY", ""))
        if not expected:
            return _error("API key is not configured", 503)
        provided = request.headers.get("Authorization", "")
        if not hmac.compare_digest(provided, "Bearer " + expected):
            return _error("authentication required", 401)
        return view(*args, **kwargs)

    return wrapped


def get_db() -> sqlite3.Connection:
    if "db" not in g:
        g.db = sqlite3.connect(current_app.config["DATABASE"])
        g.db.row_factory = sqlite3.Row
    return g.db


def _init_db() -> None:
    db = get_db()
    db.executescript(
        """
        CREATE TABLE IF NOT EXISTS call_logs (
            device_id TEXT NOT NULL,
            call_id TEXT NOT NULL,
            number TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            duration_seconds INTEGER NOT NULL,
            type TEXT NOT NULL CHECK(type IN ('INCOMING','OUTGOING','MISSED')),
            created_at INTEGER NOT NULL,
            PRIMARY KEY (device_id, call_id)
        );
        CREATE INDEX IF NOT EXISTS idx_call_logs_device_timestamp
            ON call_logs(device_id, timestamp DESC);
        CREATE INDEX IF NOT EXISTS idx_call_logs_device_type
            ON call_logs(device_id, type);
        """
    )
    db.commit()


def _valid_text(value: Any, max_length: int) -> bool:
    return isinstance(value, str) and bool(value.strip()) and len(value) <= max_length


def _validate_call(call: Any) -> str | None:
    if not isinstance(call, dict):
        return "call must be an object"
    if not _valid_text(call.get("number"), MAX_TEXT_LENGTH):
        return "number is required"
    timestamp = call.get("timestamp")
    if isinstance(timestamp, bool) or not isinstance(timestamp, int) or timestamp <= 0:
        return "timestamp must be a positive integer"
    duration = call.get("durationSeconds")
    if isinstance(duration, bool) or not isinstance(duration, int) or not 0 <= duration <= 86_400:
        return "durationSeconds must be an integer from 0 to 86400"
    call_type = call.get("type")
    if not isinstance(call_type, str) or call_type.upper() not in CALL_TYPES:
        return "type must be INCOMING, OUTGOING, or MISSED"
    contact_name = call.get("contactName")
    if contact_name not in (None, ""):
        return "contactName is device-local and must not be uploaded"
    return None


def _error(message: str, status: int):
    return jsonify(success=False, error=message), status


app = create_app()
