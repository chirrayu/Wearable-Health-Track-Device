#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// WiFi Configuration
const char* ssid     = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// Server Configuration (Replace with Backend IP)
const char* serverUrlVitals = "http://192.168.1.100:8000/vitals/";
const char* serverUrlMap    = "http://192.168.1.100:8000/map/";

// Assigned Soldier ID (UUID from Backend Database)
const char* soldierId = "SOLDIER_UUID_HERE"; 

void setup() {
    Serial.begin(115200);
    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\n[WiFi] Connected to network.");
}

void loop() {
    if (WiFi.status() == WL_CONNECTED) {
        // Mock sensor readings (replace with actual sensor driver calls)
        int heartRate = 82;
        int spo2      = 98;
        float temp    = 36.8;
        int battery   = 85;
        float lat     = 28.6139;
        float lng     = 77.2090;

        // Post Vitals
        HTTPClient http;
        http.begin(serverUrlVitals);
        http.addHeader("Content-Type", "application/json");

        StaticJsonDocument<256> vitalsDoc;
        vitalsDoc["soldier_id"] = soldierId;
        vitalsDoc["hr"]         = heartRate;
        vitalsDoc["spo2"]       = spo2;
        vitalsDoc["temp"]       = temp;
        vitalsDoc["battery"]    = battery;

        String vitalsJson;
        serializeJson(vitalsDoc, vitalsJson);
        int responseCode = http.POST(vitalsJson);
        Serial.printf("[HTTP] Vitals POST Status: %d\n", responseCode);
        http.end();

        // Post GPS Location
        http.begin(serverUrlMap);
        http.addHeader("Content-Type", "application/json");

        StaticJsonDocument<256> mapDoc;
        mapDoc["soldier_id"] = soldierId;
        mapDoc["latitude"]   = lat;
        mapDoc["longitude"]  = lng;

        String mapJson;
        serializeJson(mapDoc, mapJson);
        responseCode = http.POST(mapJson);
        Serial.printf("[HTTP] Location POST Status: %d\n", responseCode);
        http.end();
    }

    delay(5000); // 5 second sampling rate
}
