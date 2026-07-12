"""backend/blood_loss.py

Implementation of the confidence‑weighted Blood‑Loss Index (L).
The function is used by ``triage.calculate_score``.
"""

from typing import Dict

# ⚠ PLACEHOLDER BANDS — the design doc (formula_report.docx) says L_raw should
# use "the same ATLS-derived bands as before" but doesn't state the actual
# bpm/%-point cutoffs. These thresholds are a heuristic stand-in, not a
# clinical source. Replace with the real ATLS-derived trend bands once
# they're available — everything downstream (confidence gating, weighting,
# classification) is already correct and won't need to change when you do.
_RAW_STAGE_BANDS = (5, 10, 20)  # magnitude cutoffs -> stages 1, 2, 3 (else 4)


def _raw_stage(delta_hr: int, delta_spo2: int) -> int:
    # Absolute change for HR (bpm) and SpO₂ (percentage points)
    hr_abs = abs(delta_hr)
    spo2_abs = abs(delta_spo2)
    # Combine the two magnitudes – larger of the two drives the stage.
    magnitude = max(hr_abs, spo2_abs)
    if magnitude == 0:
        return 0
    b1, b2, b3 = _RAW_STAGE_BANDS
    if magnitude <= b1:
        return 1
    if magnitude <= b2:
        return 2
    if magnitude <= b3:
        return 3
    return 4


def compute_blood_loss_index(vitals, trend: Dict[str, int]) -> float:
    """Return the confidence‑weighted blood‑loss index L (0‑4).

    Parameters
    ----------
    vitals: ORM ``Vitals`` instance – must expose ``activity_index`` and the
        optional ``blast_severity``/``blast_timestamp`` fields.
    trend: ``{"delta_hr": int, "delta_spo2": int}`` – result of
        ``trend.get_trend``.
    """
    delta_hr = trend.get("delta_hr", 0)
    delta_spo2 = trend.get("delta_spo2", 0)

    # 1️⃣ Raw stage based on the magnitude of the trends
    L_raw = _raw_stage(delta_hr, delta_spo2)
    if L_raw == 0:
        return 0.0

    # 2️⃣ Confidence component C
    C = 0.4
    # Activity low (≤1) reduces chance that change is due to exertion
    if getattr(vitals, "activity_index", 3) <= 1:
        C += 0.3
    # Recent blast within 10 minutes adds confidence
    if getattr(vitals, "blast_timestamp", None) is not None:
        from datetime import datetime, timedelta
        if isinstance(vitals.blast_timestamp, datetime):
            if vitals.recorded_at - vitals.blast_timestamp <= timedelta(minutes=10):
                C += 0.3
    C = min(C, 1.0)

    L = C * L_raw
    return float(L)