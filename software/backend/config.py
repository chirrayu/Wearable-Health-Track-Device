#Environment variables, secret keys, database URL, FCM credentials, sampling rate defaults — one place for all settings.

from dotenv import load_dotenv
import os

load_dotenv()

# ── Database ──────────────────────────────────────────────────────
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./triage_ai.db")

# ── JWT Auth ──────────────────────────────────────────────────────
SECRET_KEY = os.getenv("SECRET_KEY", "change-this-in-production")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 8   # 8 hour sessions

# ── Admin credentials (change before deployment) ──────────────────
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "triage2024")

# ── Firebase (for push notifications) ────────────────────────────
FIREBASE_CREDENTIALS_PATH = os.getenv(
    "FIREBASE_CREDENTIALS_PATH",
    "firebase_credentials.json"
)

# ── Alert thresholds (match your Android rules engine) ───────────
HR_CRITICAL_THRESHOLD    = int(os.getenv("HR_CRITICAL_THRESHOLD", "130"))
SPO2_CRITICAL_THRESHOLD  = int(os.getenv("SPO2_CRITICAL_THRESHOLD", "90"))
TEMP_CRITICAL_THRESHOLD  = float(os.getenv("TEMP_CRITICAL_THRESHOLD", "103.0"))
NO_MOVEMENT_MINUTES      = int(os.getenv("NO_MOVEMENT_MINUTES", "30"))

# ── Server ────────────────────────────────────────────────────────
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8000"))
# ── AWS S3 ────────────────────────────────────────────────────────
AWS_ACCESS_KEY_ID     = os.getenv("AWS_ACCESS_KEY_ID")
AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")
AWS_REGION            = os.getenv("AWS_REGION", "ap-south-1")
S3_BUCKET_NAME        = os.getenv("S3_BUCKET_NAME", "triage-ai-photos")