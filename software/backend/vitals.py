# Receives live sensor data from suits or ESP32 devices (HR, SpO2, temp, accelerometer),
# validates it, stores it, and returns the latest readings per soldier.
from datetime import datetime
from alerts import evaluate_and_create_alerts
from websocket import push_vitals_update
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import desc
from pydantic import BaseModel
from typing import List, Optional

from database import get_db, VitalsModel, SoldierModel
from auth import get_current_admin
from config import (
    HR_CRITICAL_THRESHOLD,
    SPO2_CRITICAL_THRESHOLD,
    TEMP_CRITICAL_THRESHOLD
)
from triage import calculate_score
from blast import compute_blast_severity

router = APIRouter()


# ── Schemas ───────────────────────────────────────────────────────
class VitalsIn(BaseModel):
    soldier_id: str
    hr: Optional[int] = None
    spo2: Optional[int] = None
    temp: Optional[float] = None
    battery: Optional[int] = None
    
    # Existing extended fields
    activity_index: Optional[int] = None
    respiratory_rate: Optional[int] = None
    peak_accel_g: Optional[float] = None
    duration_ms: Optional[float] = None
    blast_timestamp: Optional[datetime] = None
    
    # NEW: ESP32 / Device tracking fields
    device_id: Optional[str] = None        # e.g., ESP32 MAC address or serial
    connection_type: Optional[str] = None  # "wifi", "ble", or "suit"


class VitalsOut(BaseModel):
    id: int
    soldier_id: str
    hr: Optional[int]
    spo2: Optional[int]
    temp: Optional[float]
    battery: Optional[int]
    recorded_at: datetime
    hr_zone: str
    status_flags: List[str]
    score: Optional[float] = None
    classification: Optional[str] = None
    
    # NEW: ESP32 tracking fields in output
    device_id: Optional[str] = None
    connection_type: Optional[str] = None

    class Config:
        from_attributes = True


# ── Helpers ───────────────────────────────────────────────────────
def get_hr_zone(hr: Optional[int]) -> str:
    if hr is None:
        return "none"
    if 50 <= hr <= 100:
        return "green"
    if 101 <= hr <= 130:
        return "yellow"
    return "red"

def get_status_flags(hr, spo2, temp, battery) -> List[str]:
    """Returns a list of active warnings for this vitals reading."""
    flags = []
    if hr is not None and hr > HR_CRITICAL_THRESHOLD:
        flags.append(f"FAST_HR:{hr}bpm")
    if spo2 is not None and spo2 < SPO2_CRITICAL_THRESHOLD:
        flags.append(f"LOW_SPO2:{spo2}%")
    if temp is not None and temp > TEMP_CRITICAL_THRESHOLD:
        flags.append(f"HIGH_TEMP:{temp}°F")
    if battery is not None and battery < 20:
        flags.append(f"LOW_BATTERY:{battery}%")
    return flags

def vitals_to_out(v: VitalsModel) -> VitalsOut:
    return VitalsOut(
        id=v.id,
        soldier_id=v.soldier_id,
        hr=v.hr,
        spo2=v.spo2,
        temp=v.temp,
        battery=v.battery,
        recorded_at=v.recorded_at,
        hr_zone=get_hr_zone(v.hr),
        status_flags=get_status_flags(v.hr, v.spo2, v.temp, v.battery),
        score=getattr(v, "score", None),
        classification=getattr(v, "classification", None),
        # NEW: Safely get ESP32 fields if they exist on the DB model
        device_id=getattr(v, "device_id", None),
        connection_type=getattr(v, "connection_type", None),
    )


# ── Core Processing Logic (Shared by HTTP and WebSocket) ────────
async def process_vitals_reading(body: VitalsIn, db: Session) -> VitalsModel:
    """
    Core logic to validate, save, score, and alert on incoming vitals.
    Shared by both the HTTP POST endpoint and the WebSocket ESP32 handler.
    """
    # Confirm soldier exists
    soldier = db.query(SoldierModel).filter(
        SoldierModel.id == body.soldier_id
    ).first()
    if not soldier:
        raise HTTPException(status_code=404, detail="Soldier not found")

    # Save vitals reading
    vitals = VitalsModel(
        soldier_id=body.soldier_id,
        hr=body.hr,
        spo2=body.spo2,
        temp=body.temp,
        battery=body.battery,
        activity_index=body.activity_index,
        respiratory_rate=body.respiratory_rate,
        blast_timestamp=body.blast_timestamp,
    )
    
    # NEW: Attach ESP32 tracking fields if the database model supports them
    if hasattr(vitals, 'device_id') and body.device_id:
        vitals.device_id = body.device_id
    if hasattr(vitals, 'connection_type') and body.connection_type:
        vitals.connection_type = body.connection_type

    db.add(vitals)

    # Compute blast severity if accelerometer data is present
    if body.peak_accel_g is not None and body.duration_ms is not None:
        if hasattr(vitals, 'blast_severity'):
            vitals.blast_severity = compute_blast_severity(
                body.peak_accel_g, body.duration_ms
            )

    # Auto-update soldier status based on vitals
    flags = get_status_flags(body.hr, body.spo2, body.temp, body.battery)
    if any("FAST_HR" in f or "LOW_SPO2" in f or "HIGH_TEMP" in f for f in flags):
        soldier.status = "critical"
    elif flags:
        soldier.status = "serious"
    elif body.hr is None and body.spo2 is None:
        soldier.status = "offline"
    else:
        soldier.status = "stable"

    db.commit()
    db.refresh(vitals)

    # Calculate TA-CSS score and classification
    calculate_score(vitals, db)
    db.commit()  # persist score/classification that calculate_score() set on the row
    db.refresh(vitals)

    # Run the rules engine and create alerts if thresholds are crossed
    await evaluate_and_create_alerts(
        soldier=soldier,
        hr=body.hr,
        spo2=body.spo2,
        temp=body.temp,
        battery=body.battery,
        db=db
    )
    
    # Push live update to connected WebSocket clients (Android app)
    await push_vitals_update(body.soldier_id, db)
    
    return vitals


# ── Routes ────────────────────────────────────────────────────────

# POST /vitals — receive vitals from a suit or ESP32 (Wi-Fi HTTP POST)
@router.post("/", response_model=VitalsOut)
async def receive_vitals(
    body: VitalsIn,
    db: Session = Depends(get_db)
):
    vitals = await process_vitals_reading(body, db)
    return vitals_to_out(vitals)


# GET /vitals/{soldier_id}/latest — get the most recent reading
@router.get("/{soldier_id}/latest", response_model=VitalsOut)
def get_latest_vitals(
    soldier_id: str,
    db: Session = Depends(get_db)
):
    vitals = db.query(VitalsModel)\
        .filter(VitalsModel.soldier_id == soldier_id)\
        .order_by(desc(VitalsModel.recorded_at))\
        .first()

    if not vitals:
        raise HTTPException(status_code=404, detail="No vitals found for this soldier")

    return vitals_to_out(vitals)


# GET /vitals/{soldier_id}/history — get last N readings
@router.get("/{soldier_id}/history", response_model=List[VitalsOut])
def get_vitals_history(
    soldier_id: str,
    limit: int = 50,
    db: Session = Depends(get_db)
):
    soldier = db.query(SoldierModel).filter(
        SoldierModel.id == soldier_id
    ).first()
    if not soldier:
        raise HTTPException(status_code=404, detail="Soldier not found")

    vitals = db.query(VitalsModel)\
        .filter(VitalsModel.soldier_id == soldier_id)\
        .order_by(desc(VitalsModel.recorded_at))\
        .limit(limit)\
        .all()

    return [vitals_to_out(v) for v in vitals]


# GET /vitals/all/latest — latest reading for every soldier at once
@router.get("/all/latest", response_model=List[VitalsOut])
def get_all_latest_vitals(db: Session = Depends(get_db)):
    soldiers = db.query(SoldierModel).all()
    result = []

    for soldier in soldiers:
        vitals = db.query(VitalsModel)\
            .filter(VitalsModel.soldier_id == soldier.id)\
            .order_by(desc(VitalsModel.recorded_at))\
            .first()

        if vitals:
            result.append(vitals_to_out(vitals))

    return result