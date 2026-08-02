#Environment variables, secret keys, database URL, FCM credentials, sampling rate defaults — one place for all settings.

from dotenv import load_dotenv
import os
import json
import tempfile
from urllib.parse import urlparse, urlunparse

# Load .env or photo.env (whichever exists)
if os.path.exists(".env"):
    load_dotenv(".env")
elif os.path.exists("photo.env"):
    load_dotenv("photo.env")
else:
    load_dotenv()  # still picks up actual env vars on Render

# ── Database ──────────────────────────────────────────────────────
def normalize_database_url(raw_url: str | None) -> str:
    if not raw_url:
        return "sqlite:///./triage_ai.db"

    if raw_url.startswith("postgres://"):
        parsed = urlparse(raw_url)
        return urlunparse(parsed._replace(scheme="postgresql+psycopg2"))

    if raw_url.startswith("postgresql://"):
        return raw_url.replace("postgresql://", "postgresql+psycopg2://", 1)

    return raw_url


DATABASE_URL = normalize_database_url(os.getenv("DATABASE_URL", "sqlite:///./triage_ai.db"))

# ── JWT Auth ──────────────────────────────────────────────────────
SECRET_KEY = os.getenv("SECRET_KEY", "change-this-in-production")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 8   # 8 hour sessions

# ── Admin credentials (change before deployment) ──────────────────
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "triage2024")

# ── Firebase (for push notifications) ────────────────────────────
# On Render you can't upload files, so pass the entire service-account
# JSON as the FIREBASE_CREDENTIALS_JSON env var.
_firebase_json_str = os.getenv("FIREBASE_CREDENTIALS_JSON")
if _firebase_json_str:
    # Write the JSON string to a temp file so firebase-admin can load it
    _tmp = tempfile.NamedTemporaryFile(
        mode="w", suffix=".json", delete=False
    )
    _tmp.write(_firebase_json_str)
    _tmp.close()
    FIREBASE_CREDENTIALS_PATH = _tmp.name
else:
    FIREBASE_CREDENTIALS_PATH = os.getenv(
        "FIREBASE_CREDENTIALS_PATH",
        "firebase_credentials.json"
    )

# ── Alert thresholds (match your Android rules engine) ────────────
# Non-login configuration (thresholds, AWS, server settings) removed to keep only login-related data
# Essential server settings restored for application startup
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8000"))
RENDER_EXTERNAL_URL = os.getenv("RENDER_EXTERNAL_URL")  # optional, used for self‑ping

# Exported symbols for explicit imports
__all__ = ["HOST", "PORT", "RENDER_EXTERNAL_URL"]