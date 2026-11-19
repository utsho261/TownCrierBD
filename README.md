> **Current update: free, on-device voice.** Read [বাংলা setup](DEPLOYMENT_BN.md). Microphone and imported files use bundled Bengali/English models; no paid speech API or Firebase deployment is required for voice. The first Android build downloads about 190 MB of public model/runtime dependencies automatically. A new APK must be built; included PREVIOUS_BUILD APKs are historical. Actual device recognition has not been tested here.

# 📢 TownCrierBD (টাউন ক্রায়ার বিডি)

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Version](https://img.shields.io/badge/Version-v2.2-blue.svg)](https://github.com/utsho261/TownCrierBD)
[![Language](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.java.com/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-amber.svg)](https://firebase.google.com/)
[![Google Maps](https://img.shields.io/badge/Maps-Google%20Maps%20SDK-blue.svg)](https://developers.google.com/maps)
[![Cloudinary](https://img.shields.io/badge/Media-Cloudinary-blueviolet.svg)](https://cloudinary.com/)

**TownCrierBD** is a modern, hyperlocal community announcement and hawker/vendor broadcast Android application tailored for Bangladesh. It connects local announcers, street vendors, and hawkers with nearby citizens in real time through GPS-based radius discovery, live interactive maps, multimedia posts (multi-image carousels & audio recordings), and in-app messaging.

---

## ✨ Features

### 📣 For Announcers (বিক্রেতা / ঘোষক)
- **Multi-Step Post Creation Wizard:** Intuitive 3-step publishing flow with categories, flat item selection, multi-image selection, and voice audio recording.
- **Audio Voice Announcements:** Record voice clips directly in-app and upload them via Cloudinary for citizens to listen to announcements aloud.
- **Multi-Image Carousel:** Attach multiple photos per announcement, displayed using an interactive image slider with dot indicators.
- **Complete Post Lifecycle Management:**
  - **Full Edit:** Modify description, categories, products, images, audio, and validity duration. Titles are automatically generated from selected items.
  - **Active / Deactivate Toggle:** Temporarily deactivate announcements at the end of the day and reactivate them with new validity when back in business.
  - **Auto-Deactivation:** Expired posts are automatically transitioned to inactive status rather than deleted permanently.
- **Competitive ETA Bidding:** Announcers can accept customer requests and bid arrival times (e.g. 25 min, 15 min). The shortest ETA dynamically updates the post for all users.
- **Multi-Route Accepted Path Tracker:** Interactive map (`Track the path`) visualizing all accepted customer delivery points, custom markers, and connected paths on a single screen.
- **In-App Real-Time Request Alerts:** Incoming customer requests prompt an immediate in-app dialog with audio/vibration for fast accept/decline.
- **Hawker Category Customization:** Quick one-tap category selection and expandable item picker in profile.

### 👥 For General Users (সাধারণ নাগরিক)
- **Proximity-Based Sorting:** Announcements are dynamically sorted in ascending order based on physical distance from the user's current GPS location.
- **Configurable Radius Filter:** Easily filter announcements within custom radii (100 m, 500 m, 1 km, 2 km, 3 km) via an interactive distance sheet.
- **Live Google Map with Custom Profile Markers:** View nearby announcers and stalls as circular profile pictures or initial badges with role-based colored rings, replacing generic pins.
- **One-Tap "Order Now":** Quick order placement directly on active announcements (`🛒 Order Now` / `🛒 অর্ডার করুন`).
- **Strict All-Item Matching:** Customer self-requests are intelligently routed only to announcers whose active posts contain ALL requested items.
- **Modern Messenger-Style Chat:**
  - **Post Offer Cards:** Embedded Facebook Messenger-style preview cards (`cardPostOffer`) displaying post image, title, and price.
  - **Waveform Audio Player & Recorder:** Sleek black capsule voice message bubbles with real-time audio waveforms, plus a live amplitude visualizer during recording.
  - **Message Seen Status:** Dynamic message delivery & read receipts (`✓ Sent` vs `✓✓ Seen`).
  - **Live Online Presence:** Real-time presence indicators ("Online / Active now", "Active 5m ago").
  - **Edit & Delete:** In-place message editing and real-time deletion for both sender and receiver.

### 🔔 Notifications & Privacy
- **Firebase Cloud Messaging (FCM):** Background push notifications for nearby broadcasts, new requests, and incoming chat messages.
- **Granular User Controls:** Notification settings toggles available on General User profiles (hidden from Announcer profiles where notifications are essential).
- **Clean Logout & Notification Suppression:** When logged out, the device's FCM token is cleanly removed from Firebase and unregistered locally to prevent unwanted notifications after session end.

### 🌐 Bilingual Experience
- Seamless English and Bengali (বাংলা) interface for all buttons, menus, dialogs, and error messages.

---

## 🛠️ Technology Stack

| Component | Technology / Library |
|---|---|
| **OS Platform** | Android (API Level 24+ / Target SDK 34) |
| **Language** | Java (JDK 17) |
| **Authentication** | Firebase Authentication (Phone / Email) |
| **Database** | Firebase Realtime Database |
| **Push Notifications** | Firebase Cloud Messaging (FCM) + Render Backend Server |
| **Media Storage** | Cloudinary Android SDK & Cloudinary REST API |
| **Maps & Location** | Google Maps SDK for Android (`play-services-maps`), Location Services (`play-services-location`), Android Maps Utils |
| **Image Loading** | Glide v4.16.0 |
| **UI Components** | Material Components 1.12.0, ViewPager2, SwipeRefreshLayout |

---

## 📁 Project Structure

```
TownCrierBD/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/towncrierbd/
│   │       │   ├── activities/       # Login, Signup, Feeds, Maps, Profile, AddAnnouncement, Chat, etc.
│   │       │   ├── adapters/         # FeedAdapter, ImageSliderAdapter, ChatAdapter, etc.
│   │       │   ├── models/           # Announcement, User, Message, Category, etc.
│   │       │   └── utils/            # AuthUtils, Constants, AudioRecorderHelper, RadiusManager, etc.
│   │       ├── res/                  # Layouts, drawables, strings, navigation
│   │       └── AndroidManifest.xml   # App manifest with permissions & components
│   ├── build.gradle.kts              # App module build config & dependency definitions
│   └── google-services.json.example  # Dummy Firebase configuration template
├── local.properties.example          # API keys & environment variables template
├── build.gradle.kts                  # Root project build script
└── README.md
```

---

## 🚀 Getting Started & Local Setup

### 1. Prerequisites
- **Android Studio** (Hedgehog / Iguana / Jellyfish / Ladybug or newer)
- **JDK 17** (e.g., Microsoft OpenJDK 17 or Eclipse Temurin 17)
- Android device or emulator running Android 7.0 (API 24) or higher with Google Play Services.

### 2. Clone the Repository
```bash
git clone https://github.com/utsho261/TownCrierBD.git
cd TownCrierBD
```

### 3. Configure Local API Keys (`local.properties`)
Sensitive keys are isolated from version control. Copy the example template:
```bash
cp local.properties.example local.properties
```
Open `local.properties` and specify your credentials:
```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk

# ── Required API Keys ───────────────────────────────────────────────
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
CLOUDINARY_CLOUD_NAME=YOUR_CLOUDINARY_CLOUD_NAME
CLOUDINARY_UPLOAD_PRESET=YOUR_CLOUDINARY_UPLOAD_PRESET
SERVER_URL=https://your-notification-server.onrender.com
```

### 4. Configure Firebase (`google-services.json`)
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.example.towncrierbd`.
3. Enable **Authentication**, **Realtime Database**, and **Cloud Messaging**.
4. Download `google-services.json` and place it in the `app/` directory:
   ```
   app/google-services.json
   ```
   *(A reference template is available at [`app/google-services.json.example`](app/google-services.json.example)).*

### 5. Build and Run
Using Android Studio:
- Click **Sync Project with Gradle Files**.
- Select your target device and click **Run (Shift + F10)**.

Using Command Line:
```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\gradlew.bat assembleDebug

# Linux / macOS
export JAVA_HOME=/path/to/jdk-17 && ./gradlew assembleDebug
```

---

## 🔒 Security & Privacy Practices
- **Decoupled Secrets:** No API keys, Cloudinary upload presets, or private keys are hardcoded in source code.
- **Git Ignored Credentials:** `google-services.json`, `local.properties`, and keystore files (`*.jks`, `*.keystore`) are strictly ignored by `.gitignore` to prevent credential exposure when pushing to public repositories.
- **Build-Time Injection:** Secrets are injected at compilation time using Gradle's `BuildConfig` and `manifestPlaceholders`.

---

## 📄 License
This project is developed for community announcement and vendor discovery in Bangladesh. All rights reserved.


## Teacher-requested updates in this version

- Recorded or selected audio is decoded and transcribed; Bangla/English keywords select items after the voice/details step. No filename/metadata guessing.
- Flat, alphabetically sorted categories; **Add category** in the category editor saves a reusable category and its items.
- General feed: category dropdown, **All / My requests**, distance sorting, and streamlined post cards.
- Announcer feed: nearest customer requests, five at a time with **Show 5 more**, details and **Delivered**.
- Owner receives a delivery popup; **Received** completes and hides the request. **Not received** reopens it. Request quantities and delivery seller information appear in details.
- Request creation shows offered items from active seller posts inside the selected radius. Every requested item has a quantity/unit. Phone visibility is optional; chat is the default, with phone and voice-offer choices.
- Chat messages keep their related post; voice offers can be recorded, previewed, deleted before sending, and played in the chat.
- Announcer posts use Active/Deactivate and reuse with a new duration. The announce button in My Posts sets priority and repeat frequency (once, 15, 30, or 60 minutes while active).
- Profile notification settings persist distance/category choices. Server matching honors them. Logout disables token synchronization; recipient/session checks apply even in the background.
- Map includes a distance-sorted nearby list and initially focuses the nearest post. Two recent seller location samples show approximate movement towards/away from the viewer. No movement is inferred from stale or missing samples.
- Title entry has been removed. Description remains optional; existing multi-image selection and audio preview remain available.

## 🚀 Version 2.2 Highlights

- **Custom Map Profile Markers:** Google Maps markers dynamically render user and announcer profile avatars (or initial letter badges) with role-colored borders, replacing default pins.
- **Dynamic Competitive ETA Bidding:** Multiple announcers can offer arrival times on customer requests. The lowest ETA dynamically adjusts the post deadline across all devices. If an accepted announcer does not arrive in time, the request automatically falls back to the next fastest bidder.
- **Announcer Multi-Path Route Map:** Announcers can accept multiple concurrent requests and view all customer destinations and suggested routes on a single screen via the `Track the path` button.
- **Messenger-Style Chat Experience:**
  - Integrated Post Offer Cards (`cardPostOffer`) for fast context preview.
  - Black capsule voice bubbles with animated audio waveforms and customized live recorder dialog.
  - Read receipts (`✓✓ Seen` vs `✓ Sent`), real-time online/last-seen presence, and in-place message edit and delete.
- **Strict All-Item Inventory Matching:** Customer self-requests are strictly dispatched only to announcers possessing all requested items in their active posts.
- **Feed Polish & Clean UI:** Replaced generic request buttons with `🛒 Order Now` / `🛒 অর্ডার করুন` and removed redundant `★ Posts` action buttons from feeds.
- **Crash-Resistant App Updater:** Re-engineered `AppUpdateManager` to safely parse version codes and names, supporting seamless in-app OTA APK downloads.
- **Binary Footprint Optimization:** Filtered emulator architectures (`arm64-v8a`, `armeabi-v7a`), trimming down release APK size significantly.

## Current voice and backend setup

See [DEPLOYMENT_BN.md](DEPLOYMENT_BN.md) for Windows/Android Studio steps.

Microphone and File both feed the same on-device recognizer. M4A/MP3 and other Android-supported formats use MediaCodec; PCM16/float32 WAV uses a bounded RIFF reader. PCM channels are mixed to normalized mono and sherpa-onnx resamples internally. Continuous streaming preserves words across codec buffers and flushes final words. There is no request to Firebase or any speech API. Local model/audio work is serialized, cancellable and off the UI thread.

The APK bundles Apache-2.0 Bengali and English models. Gradle's `prepareOfflineVoice` downloads the pinned official runtime/models, validates binary SHA256/size and vocabulary shape, and includes the assets. About 190 MB is fetched once, plus normal Android dependencies. The cache survives Clean Project. Publication still uploads the original audio through the existing media service.

The earlier request lifecycle, notifications and tracking features remain dependent on a separately deployed Firebase backend; they have not been converted to free hosting. Do not enable billing or deploy functions to enable voice. The paid `transcribeAudio` implementation and deployment/smoke-test instructions have been removed. Existing remote functions are not automatically changed by a source ZIP.

Current test evidence: [tests/current-update-results.md](tests/current-update-results.md).

## Validation recorded for the previous version (not rerun as a full Android build)

- `:app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL. All 14 JVM tests pass.
- `node --test functions/workflow.test.js`: all 12 backend tests pass.
- Six callable authentication/input-validation checks pass without contacting live services.
- Backend workflow tests cover distance/category/radius matching, logout opt-out,
  delivery ownership, competing delivery claims, and receipt transitions.
- Cloud Functions exports and callable input/authentication guards are checked locally.
- Live Firebase/FCM/Speech/Maps behavior still needs testing on your configured account
  and Android device after deployment. No live backend or credentials were changed.
