import requests

BASE = "http://localhost:8000"

token = requests.post(f"{BASE}/auth/login", data={
    "username": "admin",
    "password": "triage2024"
}).json()["access_token"]

headers = {"Authorization": f"Bearer {token}"}
soldiers = requests.get(f"{BASE}/soldiers/").json()

vitals_data = [
    {"hr": 72,  "spo2": 98, "temp": 98.6,  "battery": 87},
    {"hr": 68,  "spo2": 99, "temp": 98.2,  "battery": 92},
    {"hr": 108, "spo2": 93, "temp": 100.4, "battery": 61},
    {"hr": 138, "spo2": 84, "temp": 103.1, "battery": 34},
    {"hr": None,"spo2": None,"temp": None, "battery": 5},
    {"hr": 65,  "spo2": 98, "temp": 98.4,  "battery": 79},
]

for i, soldier in enumerate(soldiers):
    v = vitals_data[i] if i < len(vitals_data) else vitals_data[0]
    r = requests.post(f"{BASE}/vitals/", json={
        "soldier_id": soldier["id"],
        **v
    })
    print(f"Vitals posted for {soldier['name']} → {r.status_code}")

print("Done")