import requests

BASE = "http://localhost:8000"

# Login
print("Logging in...")
token = requests.post(f"{BASE}/auth/login", data={
    "username": "admin",
    "password": "triage2024"
}).json()["access_token"]

headers = {"Authorization": f"Bearer {token}"}
print("Logged in successfully")

# ── Create or fetch squads ────────────────────────────────────────
squads = {}

# Get existing squads first
existing_squads = requests.get(f"{BASE}/squads/").json()
for s in existing_squads:
    squads[s["name"]] = s["id"]
    print(f"Found existing squad: {s['name']} → {s['id']}")

# Create any missing squads
for name in ["Alpha", "Bravo", "Charlie", "Delta"]:
    if name not in squads:
        r = requests.post(f"{BASE}/squads/", json={"name": name}, headers=headers)
        if r.status_code == 200:
            squads[name] = r.json()["id"]
            print(f"Created squad: {name} → {squads[name]}")
        else:
            print(f"Failed to create squad {name}: {r.text}")

# ── Create or skip soldiers ───────────────────────────────────────
existing_soldiers = requests.get(f"{BASE}/soldiers/").json()
existing_serials  = [s["serial"] for s in existing_soldiers]

soldiers_data = [
    {"name": "Marcus Webb",  "rank_title": "Cpt.", "rank_order": 1, "serial": "S-001", "squad_id": squads.get("Alpha",   ""), "role": "Squad Leader", "blood_group": "O+",  "status": "stable"},
    {"name": "Rina Patel",   "rank_title": "Sgt.", "rank_order": 2, "serial": "S-002", "squad_id": squads.get("Alpha",   ""), "role": "Medic",        "blood_group": "A+",  "status": "stable"},
    {"name": "James Okafor", "rank_title": "Cpl.", "rank_order": 3, "serial": "S-003", "squad_id": squads.get("Bravo",   ""), "role": "Rifleman",     "blood_group": "B+",  "status": "serious"},
    {"name": "Ethan Cruz",   "rank_title": "Pvt.", "rank_order": 4, "serial": "S-004", "squad_id": squads.get("Bravo",   ""), "role": "Rifleman",     "blood_group": "AB-", "status": "critical"},
    {"name": "Yuki Tanaka",  "rank_title": "Sgt.", "rank_order": 2, "serial": "S-005", "squad_id": squads.get("Charlie", ""), "role": "Scout",        "blood_group": "O-",  "status": "offline"},
    {"name": "Sarah Novak",  "rank_title": "Lt.",  "rank_order": 1, "serial": "S-006", "squad_id": squads.get("Charlie", ""), "role": "Sniper",       "blood_group": "A-",  "status": "stable"},
]

for s in soldiers_data:
    if s["serial"] in existing_serials:
        print(f"Skipping {s['name']} — already exists")
        continue

    r = requests.post(f"{BASE}/soldiers/", json=s, headers=headers)
    if r.status_code == 200:
        print(f"Created soldier: {s['name']} → {r.json()['id']}")
    else:
        print(f"Failed to create {s['name']}: {r.text}")

print("\nDone — database seeded")