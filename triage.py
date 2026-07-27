"""backend/triage.py

TA-CSS scorer implementation, per formula_report.docx.
vitals.py calls calculate_score() with a freshly-saved VitalsModel row and
the active DB session. Returns a dict with the numeric severity score and
the textual classification band, and also writes both onto the vitals
row (caller is responsible for committing).
"""

from typing import Dict
from sqlalchemy.orm import Session

# Weight constants (from formula_report.docx §1.2)
W1 = 0.8  # Heart Rate
W2 = 1.3  # SpO2
W3 = 0.9  # Activity Index
W4 = 1.2  # Respiratory Rate
GAMMA = 1.0  # Hemorrhage weighting (§1.5 — reduced from 1.5 to 1.0)

# Sub-score lookup tables – each maps a raw measurement to a 0-3 score
def _hr_score(hr: int) -> int:
    # Boundary at 140 made exclusive (was <=140) to match formula_report.docx's
    # own Example A worked calculation, which scores hr=140 as sub-score 3.
    if 60 <= hr <= 100:
        return 0
    if 100 < hr <= 120 or 50 <= hr < 60:
        return 1
    if 120 < hr < 140 or 40 <= hr < 50:
        return 2
    return 3  # hr >= 140 or hr < 40

def _spo2_score(spo2: int) -> int:
    if 95 <= spo2 <= 100:
        return 0
    if 91 <= spo2 <= 94:
        return 1
    if 86 <= spo2 <= 90:
        return 2
    return 3  # <=85

def _activity_score(activity: int) -> int:
    # The activity index is already a 0-3 categorical value.
    return activity

def _rr_score(rr: int) -> int:
    if 12 <= rr <= 20:
        return 0
    if 21 <= rr <= 24 or 9 <= rr <= 11:
        return 1
    if 25 <= rr <= 30 or 6 <= rr <= 8:
        return 2
    return 3  # >30 or <6

# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------
def calculate_score(vitals, db: Session) -> Dict[str, float] | None:
    """Calculate the TA-CSS severity score for a single Vitals record.

    Parameters
    ----------
    vitals: the VitalsModel row just saved by vitals.py (database.py schema).
        Requires hr, spo2, activity_index, respiratory_rate to be present —
        if any are missing (e.g. a device that only sends hr/spo2/temp),
        scoring is skipped and None is returned rather than guessing.
    db: active SQLAlchemy session, used to fetch the soldier's recent
        vitals history for the trend calculation.

    Returns
    -------
    dict | None
        ``{"score": <float>, "classification": <str>}``, or None if the
        reading doesn't have enough fields to score.
    """
    if vitals.hr is None or vitals.spo2 is None:
        return None
    if vitals.activity_index is None or vitals.respiratory_rate is None:
        # No accelerometer/respiration data on this reading — can't compute
        # the full TA-CSS score. Threshold-based alerts (alerts.py) still
        # run independently of this.
        return None

    # 1) Normalise each vital into a 0-3 sub-score
    hr_sub = _hr_score(vitals.hr)
    spo2_sub = _spo2_score(vitals.spo2)
    act_sub = _activity_score(vitals.activity_index)
    rr_sub = _rr_score(vitals.respiratory_rate)

    # 2) Weighted sum of the four sub-scores
    weighted_sum = (
        W1 * hr_sub +
        W2 * spo2_sub +
        W3 * act_sub +
        W4 * rr_sub
    )

    # 3) Contextual multipliers (flat imports — this project has no
    #    package structure, so relative imports like `from .blood_loss`
    #    will not work here)
    from blood_loss import compute_blood_loss_index
    from blast import compute_blast_multiplier
    from trend import get_trend

    trend = get_trend(db, vitals.soldier_id, vitals.recorded_at)
    L = compute_blood_loss_index(vitals, trend)
    B = compute_blast_multiplier(vitals)

    # 4) Final formula (formula_report.docx §1.5)
    score = B * weighted_sum + GAMMA * L

    # 5) Classification bands (§1.6)
    if score <= 6.5:
        classification = "Stable"
    elif score <= 13.5:
        classification = "Serious"
    else:
        classification = "Critical"

    # Persist onto the row — caller (vitals.py) commits.
    vitals.score = score
    vitals.classification = classification

    return {"score": score, "classification": classification}
