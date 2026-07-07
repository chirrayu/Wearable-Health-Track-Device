#Sends Firebase Cloud Messaging (FCM) push notifications to the Android device when a critical alert fires 
# — so the app doesn't need to be open.
import firebase_admin
from firebase_admin import credentials, messaging
from typing import Optional
import json
import os

from config import FIREBASE_CREDENTIALS_PATH


# ── Firebase init ─────────────────────────────────────────────────
# Only initialize once — Firebase SDK throws if you call this twice.
_firebase_initialized = False

def init_firebase():
    global _firebase_initialized
    if _firebase_initialized:
        return

    if not os.path.exists(FIREBASE_CREDENTIALS_PATH):
        print(
            f"WARNING: Firebase credentials not found at "
            f"{FIREBASE_CREDENTIALS_PATH}. "
            f"Push notifications will be disabled."
        )
        return

    try:
        cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
        firebase_admin.initialize_app(cred)
        _firebase_initialized = True
        print("Firebase initialized successfully")
    except Exception as e:
        print(f"Firebase init failed: {e}")


# ── Device token store ────────────────────────────────────────────
# In production this would be stored in the database per operator/device.
# For now it's a simple in-memory store that resets on server restart.
# You register the token when the Android app first connects.
_device_tokens: list[str] = []

def register_device_token(token: str):
    """Called when the Android app sends its FCM token on startup."""
    if token and token not in _device_tokens:
        _device_tokens.append(token)
        print(f"Device registered | total devices: {len(_device_tokens)}")

def unregister_device_token(token: str):
    """Called when a device logs out."""
    if token in _device_tokens:
        _device_tokens.remove(token)
        print(f"Device unregistered | total devices: {len(_device_tokens)}")

def get_device_tokens() -> list[str]:
    return _device_tokens.copy()


# ── Notification builders ─────────────────────────────────────────
def build_notification(
    title: str,
    body: str,
    severity: str,
    soldier_name: str,
    soldier_serial: str,
    alert_id: str
) -> dict:
    """
    Builds the FCM message payload.
    data dict is received by the Android app even when it's in background.
    notification dict shows the system notification in the shade.
    """
    return {
        "title": title,
        "body": body,
        "data": {
            "alert_id":      alert_id,
            "severity":      severity,
            "soldier_name":  soldier_name,
            "soldier_serial": soldier_serial,
            "screen":        "Alerts"   # Android opens this screen on tap
        }
    }


# ── Send functions ────────────────────────────────────────────────
def send_push_notification(
    title: str,
    body: str,
    severity: str,
    soldier_name: str,
    soldier_serial: str,
    alert_id: str,
    token: Optional[str] = None
) -> bool:
    """
    Sends a push notification to one specific device token,
    or to all registered devices if no token is given.
    Returns True if at least one notification was sent successfully.
    """
    if not _firebase_initialized:
        print("Push skipped — Firebase not initialized")
        return False

    tokens = [token] if token else get_device_tokens()
    if not tokens:
        print("Push skipped — no devices registered")
        return False

    payload = build_notification(
        title=title,
        body=body,
        severity=severity,
        soldier_name=soldier_name,
        soldier_serial=soldier_serial,
        alert_id=alert_id
    )

    # Android notification priority based on severity
    android_priority = (
        messaging.AndroidMessagePriority.HIGH
        if severity == "critical"
        else messaging.AndroidMessagePriority.NORMAL
    )

    success_count = 0

    for device_token in tokens:
        try:
            message = messaging.Message(
                notification=messaging.Notification(
                    title=payload["title"],
                    body=payload["body"]
                ),
                data=payload["data"],
                android=messaging.AndroidConfig(
                    priority=android_priority,
                    notification=messaging.AndroidNotification(
                        # Color of notification icon in status bar
                        color="#FF445A" if severity == "critical" else
                              "#FFC533" if severity == "warning" else
                              "#00C2FF",
                        sound="default",
                        # Vibration pattern for critical alerts
                        # [delay, on, off, on, off] in milliseconds
                        vibrate_timings_millis=[0, 500, 200, 500]
                        if severity == "critical" else [0, 200]
                    )
                ),
                token=device_token
            )

            response = messaging.send(message)
            print(f"Push sent | severity: {severity} | response: {response}")
            success_count += 1

        except messaging.UnregisteredError:
            # Token expired — remove it
            print(f"Removing expired token: {device_token[:20]}...")
            unregister_device_token(device_token)

        except Exception as e:
            print(f"Push failed for token {device_token[:20]}...: {e}")

    return success_count > 0


def send_critical_alert(
    soldier_name: str,
    soldier_serial: str,
    alert_title: str,
    alert_message: str,
    alert_id: str
):
    """Shortcut for sending a critical alert notification."""
    return send_push_notification(
        title=f"🚨 {alert_title}",
        body=f"{soldier_name} ({soldier_serial}) — {alert_message}",
        severity="critical",
        soldier_name=soldier_name,
        soldier_serial=soldier_serial,
        alert_id=alert_id
    )


def send_warning_alert(
    soldier_name: str,
    soldier_serial: str,
    alert_title: str,
    alert_message: str,
    alert_id: str
):
    """Shortcut for sending a warning notification."""
    return send_push_notification(
        title=f"⚠️ {alert_title}",
        body=f"{soldier_name} ({soldier_serial}) — {alert_message}",
        severity="warning",
        soldier_name=soldier_name,
        soldier_serial=soldier_serial,
        alert_id=alert_id
    )


def send_info_alert(
    soldier_name: str,
    soldier_serial: str,
    alert_title: str,
    alert_message: str,
    alert_id: str
):
    """Shortcut for sending an informational notification."""
    return send_push_notification(
        title=f"ℹ️ {alert_title}",
        body=f"{soldier_name} ({soldier_serial}) — {alert_message}",
        severity="information",
        soldier_name=soldier_name,
        soldier_serial=soldier_serial,
        alert_id=alert_id
    )