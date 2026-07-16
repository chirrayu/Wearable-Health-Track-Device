import requests
import time
import random

BASE = "http://localhost:8000"

# Login
print("Logging in...")
response = requests.post(f"{BASE}/auth/login", data={
    "username": "admin",
    "password": "triage2024"
})

if response.status_code != 200:
    print(f"Login failed: {response.text}")
    exit()

token = response.json()["access_token"]
headers = {"Authorization": f"Bearer {token}"}
print("Logged in successfully")

# Get all soldiers
soldiers = requests.get(f"{BASE}/soldiers/").json()
active = [s for s in soldiers if s["status"] != "offline"]

if not active:
    print("No soldiers found. Run seed.py first.")
    exit()

print(f"Simulating vitals for {len(active)} soldiers. Press Ctrl+C to stop.\n")

while True:
    for soldier in active:
        vitals = {
            "soldier_id": soldier["id"],
            "hr":      random.randint(60, 145),
            "spo2":    random.randint(82, 99),
            "temp":    round(random.uniform(97.5, 104.0), 1),
            "battery": random.randint(5, 100)
        }
        r = requests.post(f"{BASE}/vitals/", json=vitals)
        if r.status_code == 200:
            print(f"{soldier['name']} → HR: {vitals['hr']} | SpO2: {vitals['spo2']}% | Temp: {vitals['temp']}°F | Battery: {vitals['battery']}%")
        else:
            print(f"Error for {soldier['name']}: {r.text}")

    print("--- cycle complete ---\n")
    time.sleep(5)