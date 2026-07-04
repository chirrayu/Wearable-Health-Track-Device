# Wearable-Health-Track-Device

> **Real-time soldier health monitoring and AI-based triage for battlefield medical response**

---

## 📌 Project Overview

The **Smart Combat T-Shirt** is a wearable IoT + AI system embedded into a soldier's combat shirt. It continuously monitors vital signs, detects blast injuries, and transmits real-time triage data to a command center — enabling faster medical response within the **Golden Hour**.

This project is a research prototype developed as part of academic work on AI-assisted battlefield medicine.

---

## 🎯 Key Features

- 🫀 **Real-time vitals monitoring** — Heart Rate, SpO2, Body Temperature
- 💥 **Blast/explosion detection** — via accelerometer spike analysis
- 📍 **Live GPS tracking** — exact soldier location transmitted to command (not for soilder only for commercial use)
- 📡 **Wireless transmission** — via ESP32 WiFi/Bluetooth mesh network
- 🖥️ **Command Center Dashboard** — React-based live monitoring UI

---

## 🏗️ Project Structure

```
smart-combat-tshirt/
│
├── hardware/                        # Hardware Team
│
├── software/                        # Software Team
│
├── research/                        
│   ├── research_paper.pdf
│   ├── research.ppt
│   ├── patent_draft.docx
│   └── references.bib
│
├── README.md
├── .gitignore
└── LICENSE
```

---

## ⚙️ Hardware Components

| Component | Device Name | Role |
|---|---|---|
| Heart Rate + SpO2 | MAX30100 Module | Monitors pulse & blood oxygen |
| Accelerometer | MPU-6050 (GY-521) | Detects motion & blast events |
| Temperature | DS18B20 Waterproof Probe | Monitors body temperature |
| Microcontroller | ESP32 38-Pin NodeMCU | Central brain + WiFi/Bluetooth |
| GPS | NEO-6M (GY-GPS6MV2) | Real-time soldier location |
| Battery | 3.7V LiPo + TP4056 Charger | Rechargeable power supply |

---

### Branch Strategy

```
main              → stable, reviewed, tested code
Parth             → work of the code for the ESP32 chipset and all the code for the sensors
Aaditya           → work of the code for the ESP32 chipset and all the code for the sensors
Chirrayu          → building the software of the app
Abhay             → hardware building and all the hardware physical stuff(changes to the uniform and all)
```

> Both teams merge into `main` only after peer review.

---

## 📊 Expected Outcomes

- ⚡ Injury detection within **seconds** of event
- 📉 Reduced medical response time
- 🏥 Improved survival rates within the **Golden Hour**
- 🗺️ Accurate GPS-based soldier location for medic dispatch

---
