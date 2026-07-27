"""backend/blast.py

Implementation of the graded Blast Severity multiplier (B), per
formula_report.docx §1.4:

    B = 1 + BlastSeverity,   BlastSeverity ∈ [0, 0.5]

BlastSeverity is a normalized function of peak acceleration magnitude
(g-force) and spike duration from the MPU-6050, scaled linearly between a
minimum detectable threshold (BlastSeverity ≈ 0) and a saturation
threshold representing a severe proximate blast (BlastSeverity = 0.5).

Previously this file only did a lookup on a pre-existing discrete
`vitals.blast_severity` (0-4) attribute — nothing anywhere computed that
attribute from real sensor data. This version adds the actual scaling
function the design doc describes, and the multiplier now reads the
continuous 0-0.5 value directly, matching the formula exactly (no more
discrete stand-in table).
"""

# ⚠ CALIBRATION PLACEHOLDER — these two thresholds are what "minimum
# detectable" and "saturating/severe" mean in g-force + duration terms.
# The doc specifies the *shape* of the scaling (linear, 0 to 0.5) but not
# these two numbers — they need to come from real MPU-6050 calibration
# against known blast distances/charge sizes. Everything downstream
# (compute_blast_multiplier, triage.calculate_score) is already correct
# and won't need to change when these are replaced with real values.
MIN_DETECTABLE_G = 3.0     # peak acceleration below this isn't a blast
SATURATION_G = 16.0        # peak acceleration at/above this is "severe"
MIN_DURATION_MS = 5.0      # spike shorter than this is noise, not a blast
SATURATION_DURATION_MS = 80.0


def compute_blast_severity(peak_accel_g: float, duration_ms: float) -> float:
    """
    Compute BlastSeverity ∈ [0, 0.5] from a raw accelerometer spike.

    Both the magnitude and duration of the spike are scaled linearly
    into [0, 1] against their own min/saturation thresholds, then
    combined (larger of the two drives severity — a very sharp but brief
    spike and a longer but milder one can both indicate a real event).
    """
    if peak_accel_g is None or duration_ms is None:
        return 0.0
    if peak_accel_g < MIN_DETECTABLE_G:
        return 0.0

    g_fraction = (peak_accel_g - MIN_DETECTABLE_G) / (SATURATION_G - MIN_DETECTABLE_G)
    g_fraction = max(0.0, min(1.0, g_fraction))

    dur_fraction = (duration_ms - MIN_DURATION_MS) / (SATURATION_DURATION_MS - MIN_DURATION_MS)
    dur_fraction = max(0.0, min(1.0, dur_fraction))

    fraction = max(g_fraction, dur_fraction)
    return round(0.5 * fraction, 4)


def compute_blast_multiplier(vitals) -> float:
    """Return the blast multiplier B (1.0-1.5).

    Reads the continuous `blast_severity` (0.0-0.5) already stored on the
    vitals record — either set directly by compute_blast_severity() during
    ingest (see vitals.py), or supplied pre-computed by the suit firmware.
    Defaults to 0.0 (B=1.0, no blast) if unset.
    """
    severity = getattr(vitals, "blast_severity", None) or 0.0
    severity = max(0.0, min(0.5, severity))
    return 1.0 + severity
