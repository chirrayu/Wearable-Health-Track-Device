#Starts the FastAPI server, registers all routes, initializes the database and WebSocket.
from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from database import init_db
from config import HOST, PORT
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

# ── Startup: init DB + Firebase once ─────────────────────────────
@app.on_event("startup")
def on_startup():
    init_db()
    init_firebase()
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