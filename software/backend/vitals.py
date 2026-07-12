#Receives live sensor data from suits (HR, SpO2, temp, accelerometer), 
# validates it, stores it, and returns the latest readings per soldier.
from alerts import evaluate_and_create_alerts
from websocket import push_vitals_update
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import desc
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime, timedelta

from database import get_db, VitalsModel, SoldierModel
from auth import get_current_admin
from config import (
    HR_CRITICAL_THRESHOLD,
    SPO2_CRITICAL_THRESHOLD,
    TEMP_CRITICAL_THRESHOLD
)
import triage
from blast import compute_blast_severity

router = APIRouter()


# ── Schemas ───────────────────────────────────────────────────────
class VitalsIn(BaseModel):
    soldier_id: str
    hr: Optional[int] = None
    spo2: Optional[int] = None
    temp: Optional[float] = None
    battery: Optional[int] = None

    # Optional TA-CSS inputs. All new — a device that only ever sends
    # hr/spo2/temp/battery keeps working exactly as before.
    activity_index: Optional[int] = None      # 0-3, from suit accelerometer
    respiratory_rate: Optional[int] = None     # breaths/min

    # Blast context: suit can either send a pre-computed severity directly,
    # or the raw spike (peak g-force + duration) and the server computes it.
    blast_severity: Optional[float] = None     # 0.0-0.5, already normalized
    blast_peak_g: Optional[float] = None
    blast_duration_ms: Optional[float] = None

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

    # TA-CSS output — present only when the reading had enough fields
    # (activity_index + respiratory_rate) for triage.py to score it.
    score: Optional[float] = None
    classification: Optional[str] = None

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
        score=v.score,
        classification=v.classification
    )


def _resolve_blast_context(body: "VitalsIn", db: Session):
    """
    Figure out what blast_severity/blast_timestamp this reading should
    carry for triage scoring:
      1. Use a directly-supplied blast_severity if given.
      2. Else compute it from raw peak-g/duration if given.
      3. Else, if this soldier had a blast within the last 10 minutes on a
         prior reading, carry that context forward (a blast's effect on
         confidence-gating shouldn't vanish the instant the next reading
         arrives without its own blast fields).
    """
    if body.blast_severity is not None:
        return body.blast_severity, datetime.utcnow()

    if body.blast_peak_g is not None and body.blast_duration_ms is not None:
        severity = compute_blast_severity(body.blast_peak_g, body.blast_duration_ms)
        if severity > 0:
            return severity, datetime.utcnow()
        return None, None

    cutoff = datetime.utcnow() - timedelta(minutes=10)
    recent = (
        db.query(VitalsModel)
        .filter(
            VitalsModel.soldier_id == body.soldier_id,
            VitalsModel.blast_timestamp.isnot(None),
            VitalsModel.blast_timestamp >= cutoff,
        )
        .order_by(desc(VitalsModel.blast_timestamp))
        .first()
    )
    if recent:
        return recent.blast_severity, recent.blast_timestamp
    return None, None


# ── Routes ────────────────────────────────────────────────────────

# POST /vitals — receive vitals from a suit
# This is the endpoint the suit hardware (or your simulation) will
# call every few seconds to push new readings.
@router.post("/", response_model=VitalsOut)
async def receive_vitals(
    body: VitalsIn,
    db: Session = Depends(get_db)
):
    # Confirm soldier exists
    soldier = db.query(SoldierModel).filter(
        SoldierModel.id == body.soldier_id
    ).first()
    if not soldier:
        raise HTTPException(status_code=404, detail="Soldier not found")

    # Resolve blast context before creating the row (may need to look at history)
    blast_severity, blast_timestamp = _resolve_blast_context(body, db)

    # Save vitals reading
    vitals = VitalsModel(
        soldier_id=body.soldier_id,
        hr=body.hr,
        spo2=body.spo2,
        temp=body.temp,
        battery=body.battery,
        activity_index=body.activity_index,
        respiratory_rate=body.respiratory_rate,
        blast_severity=blast_severity,
        blast_timestamp=blast_timestamp
    )
    db.add(vitals)

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

    # Run the TA-CSS severity scorer (writes vitals.score/classification
    # in place if the reading has enough fields to score; no-ops otherwise)
    triage.calculate_score(vitals, db)
    db.commit()
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
    await push_vitals_update(body.soldier_id, db)
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
# This is what the Android dashboard will poll (or receive via WebSocket)
# to update all the status cards and map dots simultaneously.
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