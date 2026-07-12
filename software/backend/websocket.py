#WebSocket server so the Android app gets live updates 
#(vitals, alerts, map positions) pushed to it instead of polling every few seconds.
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends
from sqlalchemy.orm import Session
from typing import Dict, List
import asyncio
import json
from datetime import datetime

from database import get_db, SoldierModel, VitalsModel, LocationModel, AlertModel
from sqlalchemy import desc

router = APIRouter()


# ── Connection manager ────────────────────────────────────────────
# Keeps track of all active WebSocket connections.
# Each client (Android app) connects once and stays connected.
# Server pushes updates to all connected clients instantly.

class ConnectionManager:

    def __init__(self):
        # All active connections
        self.active: List[WebSocket] = []

        # Connections grouped by type so you can push
        # only to clients that care about a specific feed.
        self.subscriptions: Dict[str, List[WebSocket]] = {
            "vitals":   [],
            "alerts":   [],
            "map":      [],
            "all":      []
        }

    async def connect(self, websocket: WebSocket, feed: str = "all"):
        await websocket.accept()
        self.active.append(websocket)

        if feed in self.subscriptions:
            self.subscriptions[feed].append(websocket)
        else:
            self.subscriptions["all"].append(websocket)

        print(f"WS client connected — feed: {feed} | total: {len(self.active)}")

    def disconnect(self, websocket: WebSocket):
        self.active.remove(websocket)
        for feed_list in self.subscriptions.values():
            if websocket in feed_list:
                feed_list.remove(websocket)
        print(f"WS client disconnected | total: {len(self.active)}")

    async def push(self, message: dict, feed: str = "all"):
        """Push a message to all clients subscribed to a feed."""
        targets = self.subscriptions.get(feed, []) + self.subscriptions["all"]
        targets = list(set(targets))  # deduplicate

        dead = []
        for ws in targets:
            try:
                await ws.send_text(json.dumps(message, default=str))
            except Exception:
                dead.append(ws)

        # Clean up dead connections
        for ws in dead:
            self.disconnect(ws)

    async def push_to_all(self, message: dict):
        """Push to every connected client regardless of subscription."""
        dead = []
        for ws in self.active:
            try:
                await ws.send_text(json.dumps(message, default=str))
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)


manager = ConnectionManager()


# ── Message builders ──────────────────────────────────────────────
def build_vitals_message(soldier_id: str, db: Session) -> dict:
    vitals = db.query(VitalsModel)\
        .filter(VitalsModel.soldier_id == soldier_id)\
        .order_by(desc(VitalsModel.recorded_at))\
        .first()

    soldier = db.query(SoldierModel)\
        .filter(SoldierModel.id == soldier_id)\
        .first()

    if not vitals or not soldier:
        return {}

    return {
        "type": "vitals_update",
        "soldier_id": soldier_id,
        "soldier_name": f"{soldier.rank_title} {soldier.name}",
        "serial": soldier.serial,
        "status": soldier.status,
        "hr": vitals.hr,
        "spo2": vitals.spo2,
        "temp": vitals.temp,
        "battery": vitals.battery,
        "timestamp": vitals.recorded_at.isoformat()
    }


def build_location_message(soldier_id: str, db: Session) -> dict:
    location = db.query(LocationModel)\
        .filter(LocationModel.soldier_id == soldier_id)\
        .order_by(desc(LocationModel.recorded_at))\
        .first()

    soldier = db.query(SoldierModel)\
        .filter(SoldierModel.id == soldier_id)\
        .first()

    if not location or not soldier:
        return {}

    return {
        "type": "location_update",
        "soldier_id": soldier_id,
        "soldier_name": f"{soldier.rank_title} {soldier.name}",
        "serial": soldier.serial,
        "status": soldier.status,
        "latitude": location.latitude,
        "longitude": location.longitude,
        "timestamp": location.recorded_at.isoformat()
    }


def build_alert_message(alert: AlertModel, db: Session) -> dict:
    soldier = db.query(SoldierModel)\
        .filter(SoldierModel.id == alert.soldier_id)\
        .first()

    return {
        "type": "new_alert",
        "alert_id": alert.id,
        "soldier_id": alert.soldier_id,
        "soldier_name": f"{soldier.rank_title} {soldier.name}" if soldier else "Unknown",
        "serial": soldier.serial if soldier else "",
        "title": alert.title,
        "severity": alert.severity,
        "message": alert.message,
        "action_required": alert.action_required,
        "timestamp": alert.created_at.isoformat()
    }


def build_full_snapshot(db: Session) -> dict:
    """
    Full state snapshot — sent to a client the moment they connect
    so they immediately have current data without waiting for the
    next update cycle.
    """
    soldiers = db.query(SoldierModel).all()
    soldier_data = []

    for soldier in soldiers:
        vitals = db.query(VitalsModel)\
            .filter(VitalsModel.soldier_id == soldier.id)\
            .order_by(desc(VitalsModel.recorded_at))\
            .first()

        location = db.query(LocationModel)\
            .filter(LocationModel.soldier_id == soldier.id)\
            .order_by(desc(LocationModel.recorded_at))\
            .first()

        soldier_data.append({
            "soldier_id": soldier.id,
            "name": f"{soldier.rank_title} {soldier.name}",
            "serial": soldier.serial,
            "squad": soldier.squad_rel.name if soldier.squad_rel else None,
            "status": soldier.status,
            "hr": vitals.hr if vitals else None,
            "spo2": vitals.spo2 if vitals else None,
            "temp": vitals.temp if vitals else None,
            "battery": vitals.battery if vitals else None,
            "latitude": location.latitude if location else None,
            "longitude": location.longitude if location else None,
        })

    recent_alerts = db.query(AlertModel)\
        .order_by(desc(AlertModel.created_at))\
        .limit(20)\
        .all()

    return {
        "type": "snapshot",
        "soldiers": soldier_data,
        "alert_counts": {
            "critical": db.query(AlertModel).filter(AlertModel.severity == "critical").count(),
            "warning":  db.query(AlertModel).filter(AlertModel.severity == "warning").count(),
            "total":    db.query(AlertModel).count()
        },
        "timestamp": datetime.utcnow().isoformat()
    }


# ── WebSocket endpoints ───────────────────────────────────────────

# ws://yourserver:8000/ws/connect?feed=all
# feed options: "all" | "vitals" | "alerts" | "map"
@router.websocket("/connect")
async def websocket_connect(
    websocket: WebSocket,
    feed: str = "all"
):
    db = next(get_db())

    await manager.connect(websocket, feed)

    try:
        # Send full snapshot immediately on connect
        snapshot = build_full_snapshot(db)
        await websocket.send_text(json.dumps(snapshot, default=str))

        # Keep connection alive, listen for pings from client
        while True:
            try:
                data = await asyncio.wait_for(
                    websocket.receive_text(),
                    timeout=30.0
                )
                msg = json.loads(data)

                # Client can send a ping to keep the connection alive
                if msg.get("type") == "ping":
                    await websocket.send_text(json.dumps({
                        "type": "pong",
                        "timestamp": datetime.utcnow().isoformat()
                    }))

                # Client can request a fresh snapshot
                elif msg.get("type") == "request_snapshot":
                    snapshot = build_full_snapshot(db)
                    await websocket.send_text(
                        json.dumps(snapshot, default=str)
                    )

            except asyncio.TimeoutError:
                # Send heartbeat to keep connection alive
                await websocket.send_text(json.dumps({
                    "type": "heartbeat",
                    "timestamp": datetime.utcnow().isoformat()
                }))

    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as e:
        print(f"WebSocket error: {e}")
        manager.disconnect(websocket)
    finally:
        db.close()


# ── Push functions called from other routes ───────────────────────
# Import manager in vitals.py and alerts.py to push updates.

async def push_vitals_update(soldier_id: str, db: Session):
    """Call this from vitals.py after saving a new reading."""
    message = build_vitals_message(soldier_id, db)
    if message:
        await manager.push(message, feed="vitals")


async def push_location_update(soldier_id: str, db: Session):
    """Call this from map_tracking.py after saving a new location."""
    message = build_location_message(soldier_id, db)
    if message:
        await manager.push(message, feed="map")


async def push_alert(alert: AlertModel, db: Session):
    """Call this from alerts.py after creating a new alert."""
    message = build_alert_message(alert, db)
    if message:
        await manager.push(message, feed="alerts")