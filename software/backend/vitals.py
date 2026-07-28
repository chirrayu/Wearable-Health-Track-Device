#Receives live sensor data from suits (HR, SpO2, temp, accelerometer),
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
# ⚠ NEW — these were never imported or called anywhere in the original
# file, which is why no score was ever computed regardless of what data
# came in.
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
    # ⚠ NEW — the firmware already sends these, but this schema had no
    # fields for them, so Pydantic silently dropped them on every request.
    activity_index: Optional[int] = None
    respiratory_rate: Optional[int] = None
    peak_accel_g: Optional[float] = None
    duration_ms: Optional[float] = None
    blast_timestamp: Optional[datetime] = None  # firmware sends ISO 8601 "...Z"; pydantic parses this automatically

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
    # ⚠ NEW — surfaces the TA-CSS result computed during ingest
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
        # ⚠ NEW — these attributes must exist on VitalsModel; see note below.
        score=getattr(v, "score", None),
        classification=getattr(v, "classification", None),
    )


# ── Routes ────────────────────────────────────────────────────────

# POST /vitals — receive vitals from a suit
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

    # Save vitals reading
    vitals = VitalsModel(
        soldier_id=body.soldier_id,
        hr=body.hr,
        spo2=body.spo2,
        temp=body.temp,
        battery=body.battery,
        # ⚠ NEW — see the schema note at the bottom of this file: these
        # columns must exist on VitalsModel in database.py or this line
        # will raise a TypeError.
        activity_index=body.activity_index,
        respiratory_rate=body.respiratory_rate,
        blast_timestamp=body.blast_timestamp,
    )
    db.add(vitals)

    # ⚠ NEW — turn the raw accelerometer spike the firmware measured into
    # the continuous blast_severity (0-0.5) that blast.py's multiplier
    # reads directly off the row. Without this, B always defaulted to 1.0
    # no matter what the suit reported.
    if body.peak_accel_g is not None and body.duration_ms is not None:
        vitals.blast_severity = compute_blast_severity(
            body.peak_accel_g, body.duration_ms
        )

    # Auto-update soldier status based on vitals (unchanged — this is the
    # separate threshold-based system, distinct from the TA-CSS score)
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

    # ⚠ NEW — this is the actual missing link. Nothing previously called
    # calculate_score(), so no TA-CSS score/classification was ever
    # produced, regardless of how complete the incoming data was.
    # calculate_score() returns None (and leaves vitals.score/classification
    # untouched) if activity_index/respiratory_rate are still missing —
    # see triage.py's own docstring.
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