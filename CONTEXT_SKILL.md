# AI Context Skill: Telegram Listener App

This document serves as the "Source of Truth" for AI models assisting with the development and maintenance of the Telegram Listener Android App.

## 🚀 App Overview
A high-performance, low-battery native Android application that monitors environmental events (audio amplitude) and transmits logs to a personal Telegram chat via Bot API.

## 🛠 Tech Stack
- **Language**: Kotlin
- **Architecture**: Clean Architecture / MVVM
- **UI**: Jetpack Compose (Material 3/AppCompat Hybrid)
- **DI**: Hilt (Dagger)
- **Database**: Room (SQLite)
- **Networking**: Retrofit + OkHttp
- **Background Tasks**: 
    - `MonitorService` (Foreground Service - Microphone access)
    - `WorkManager` + `UploadWorker` (Reliable Sync)

## 📡 Essential Credentials (Hardcoded)
- **Bot Token**: `8235584686:AAEyIvuCQcL5GxynKyiXOKwyY47iV3y5CQg`
- **Chat ID**: `1400686945` (User: Nisith)

## 📂 Core Project Map
- `app/src/main/java/com/example/telegramlistener/`
    - `service/MonitorService.kt`: Listens to mic, logs audio spikes to Room.
    - `service/UploadWorker.kt`: Batches logs and sends to Telegram.
    - `data/repo/EventRepository.kt`: Bridge between Room, Retrofit, and SharedPreferences.
    - `ui/MainActivity.kt`: Compose UI for config and start/stop control.
    - `data/local/Event.kt`: Room Entity for events.

## ⚙️ Build Status & Known Issues
- **Theme Issues**: Switching from `Material3` to `AppCompat` due to resource linking errors (`Theme.Material3.DayNight.NoActionBar` not found).
- **Resource Conflicts**: Duplicate `ic_launcher` resources (XML vs PNG) occasionally cause build failures.
- **Microphone Permissions**: Requires `RECORD_AUDIO` and `FOREGROUND_SERVICE` (Microphone type).

## 🔏 Pipeline Flow
1. **Detect**: `MonitorService` -> MediaRecorder maxAmplitude.
2. **Buffer**: `EventRepository.logEvent` -> `Room` Database.
3. **Trigger**: `WorkManager` (every 15 mins) OR constraints met.
4. **Transmit**: `UploadWorker` -> `TelegramApi.sendMessage` -> Clear Room.
