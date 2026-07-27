# trend.py
"""Trend utilities for computing rolling 5-minute changes in HR/SpO2.

Previously this took a pre-built `vitals_history` list that nothing in the
codebase ever constructed. It now queries the real vitals table directly,
so triage.py can call it with just a DB session, soldier_id, and the
current reading's timestamp.
"""
from typing import Dict
from datetime import datetime, timedelta
from sqlalchemy.orm import Session

WINDOW_MINUTES = 5


def get_trend(db: Session, soldier_id: str, current_timestamp: datetime) -> Dict[str, float]:
    """Compute ΔHR and ΔSpO2 over the past 5 minutes for one soldier.

    Parameters
    ----------
    db: active SQLAlchemy session.
    soldier_id: the soldier whose vitals history to look at.
    current_timestamp: timestamp of the current reading.

    Returns
    -------
    dict
        ``{"delta_hr": float, "delta_spo2": float}``
        If there isn't a reading at least ~5 minutes old yet, deltas are 0.0
        (matches the original placeholder behavior for a soldier with no
        history yet).
    """
    from database import VitalsModel  # local import avoids a circular import at module load

    window_start = current_timestamp - timedelta(minutes=WINDOW_MINUTES)

    # Most recent reading at/before the window start —
    # i.e. the closest thing we have to "the reading ~5 minutes ago".
    earlier = (
        db.query(VitalsModel)
        .filter(
            VitalsModel.soldier_id == soldier_id,
            VitalsModel.recorded_at <= window_start,
        )
        .order_by(VitalsModel.recorded_at.desc())
        .first()
    )

    # The current/most recent reading at or before current_timestamp.
    later = (
        db.query(VitalsModel)
        .filter(
            VitalsModel.soldier_id == soldier_id,
            VitalsModel.recorded_at <= current_timestamp,
        )
        .order_by(VitalsModel.recorded_at.desc())
        .first()
    )

    if not earlier or not later or earlier.hr is None or later.hr is None:
        return {"delta_hr": 0.0, "delta_spo2": 0.0}

    delta_hr = (later.hr or 0) - (earlier.hr or 0)
    delta_spo2 = (later.spo2 or 0) - (earlier.spo2 or 0)
    return {"delta_hr": float(delta_hr), "delta_spo2": float(delta_spo2)}
