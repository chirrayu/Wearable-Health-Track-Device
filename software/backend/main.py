#Starts the FastAPI server, registers all routes, initializes the database and WebSocket.
from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from database import init_db
from config import HOST, PORT, RENDER_EXTERNAL_URL, validate_production_settings
from auth import get_current_admin
from alerts_notifier import init_firebase, register_device_token, unregister_device_token

from auth import router as auth_router
from soldiers import router as soldiers_router
from vitals import router as vitals_router
from alerts import router as alerts_router
from squads import router as squads_router
from suit_config import router as suit_config_router
from map_tracking import router as map_router
from websocket import router as ws_router

import asyncio
import urllib.request

app = FastAPI(
    title="Triage AI Backend",
    version="1.0.0"
)

# ── CORS (allows your Android app to reach the API) ───────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # tighten this to your server IP in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Self-ping to keep Render free tier alive ─────────────────────
async def self_ping():
    """Ping our own health endpoint every 14 minutes to prevent
    Render free-tier cold starts."""
    if not RENDER_EXTERNAL_URL:
        print("RENDER_EXTERNAL_URL not set — self-ping disabled (local dev)")
        return
    url = f"{RENDER_EXTERNAL_URL}/"
    print(f"Self-ping started → {url} every 14 min")
    while True:
        await asyncio.sleep(14 * 60)  # 14 minutes
        try:
            urllib.request.urlopen(url, timeout=10)
            print("Self-ping OK")
        except Exception as e:
            print(f"Self-ping failed: {e}")


# ── Startup: init DB + Firebase + self-ping ──────────────────────
@app.on_event("startup")
async def on_startup():
    validate_production_settings()
    init_db()
    init_firebase()
    asyncio.create_task(self_ping())
    print("Server ready")


# ── Health check ──────────────────────────────────────────────────
@app.get("/")
def health():
    return {"status": "ok", "app": "Triage AI Backend"}


# ── Device token registration (admin-only) ────────────────────────
class DeviceTokenIn(BaseModel):
    token: str

@app.post("/device/register", tags=["Device"])
def register_device(body: DeviceTokenIn, admin=Depends(get_current_admin)):
    """
    Android app calls this on startup with its FCM token.
    Server stores the token and uses it to send push notifications.
    """
    register_device_token(body.token)
    return {"message": "Device registered"}

@app.post("/device/unregister", tags=["Device"])
def unregister_device(body: DeviceTokenIn, admin=Depends(get_current_admin)):
    """Called when operator logs out."""
    unregister_device_token(body.token)
    return {"message": "Device unregistered"}


# ── Routers ───────────────────────────────────────────────────────
app.include_router(auth_router,        prefix="/auth",       tags=["Auth"])
app.include_router(soldiers_router,    prefix="/soldiers",   tags=["Soldiers"])
app.include_router(vitals_router,      prefix="/vitals",     tags=["Vitals"])
app.include_router(alerts_router,      prefix="/alerts",     tags=["Alerts"])
app.include_router(squads_router,      prefix="/squads",     tags=["Squads"])
app.include_router(suit_config_router, prefix="/suit",       tags=["Suit Config"])
app.include_router(map_router,         prefix="/map",        tags=["Map"])
app.include_router(ws_router,          prefix="/ws",         tags=["WebSocket"])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=HOST, port=PORT, reload=True)
