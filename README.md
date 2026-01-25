# Gate Opener - Android App for Shelly Pro 1 Gate Control

An Android application designed for old phones (Android 4.1.2+) that monitors incoming calls and triggers a Shelly Pro 1 relay to open a gate when a whitelisted number calls.

## Features

- **24/7 Background Service**: Runs continuously as a foreground service with wake lock
- **Call Detection**: Monitors all incoming phone calls
- **Whitelist Management**: Only triggers gate for approved phone numbers
- **Automatic Call Rejection**: Rejects ALL incoming calls (whitelisted or not)
- **Shelly Pro 1 Integration**: Sends HTTP POST to trigger the relay
- **Auto-start on Boot**: Service starts automatically when phone boots
- **Activity Logging**: Shows recent call activity in the app
- **Network Configuration**: Load config from a JSON file on your network (NAS, web server, etc.)

## Requirements

- Android 4.1.2 (API 16) or higher
- Phone with SIM card capability
- WiFi connection to local network with Shelly Pro 1

## Building the APK

### Prerequisites

1. Install [Android Studio](https://developer.android.com/studio) or use command-line tools
2. Install Android SDK with API level 16-28
3. Install Java JDK 8 or higher

### Build Steps

#### Option 1: Android Studio
1. Open the project folder in Android Studio
2. Wait for Gradle sync to complete
3. Build > Build Bundle(s) / APK(s) > Build APK(s)
4. APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

#### Option 2: Command Line
```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

## Installation

### Enable Unknown Sources
On your Huawei Ascend Mate (MT1-U06):
1. Go to **Settings > Security**
2. Enable **Unknown sources**

### Install APK
1. Copy the APK to your phone via USB or download it
2. Open a file manager and tap the APK
3. Follow the installation prompts
4. Grant all requested permissions

## Configuration

### 1. Set Shelly URL
Enter your Shelly Pro 1's IP address in the URL field:
```
http://192.168.1.100
```

The app will automatically try both:
- Shelly Pro 1 API: `/rpc/Switch.Set`
- Shelly Gen1 API: `/relay/0?turn=on`

### 2. Add Whitelist Numbers
Enter phone numbers that should trigger the gate, one per line:
```
+32471234567
0471234567
+32 471 23 45 67
```

The app normalizes numbers and matches by last 9 digits, so different formats will work.

### 3. Start the Service
Tap **Start Service** to begin monitoring calls.

### 4. Test Connection
Use the **Test Shelly Connection** button to verify the gate triggers.

## How It Works

```
Incoming Call
     │
     ▼
┌─────────────────┐
│ Get Caller ID   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     NO      ┌─────────────────┐
│ Is Whitelisted? │────────────►│  Reject Call    │
└────────┬────────┘             └─────────────────┘
         │ YES
         ▼
┌─────────────────┐
│ HTTP POST to    │
│ Shelly Pro 1    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Reject Call    │
└─────────────────┘
```

## Network Configuration (Optional)

Instead of manually configuring the app, you can host a JSON config file on your network and the app will automatically reload it every 5 minutes.

### Setup

1. Create a JSON file (see `sample-config.json`) on a web server or NAS
2. Make it accessible via HTTP (e.g., `http://192.168.1.50/gateopener.json`)
3. Enter the URL in the app's "Network Config URL" field
4. Click "Reload Config Now" to test

### JSON Config Format

```json
{
  "shelly_url": "http://192.168.1.100",
  "shelly_method": "POST",
  "shelly_endpoint": "/rpc/Switch.Set",
  "shelly_payload": "{\"id\":0,\"on\":true}",
  "whitelist": [
    "+32471234567",
    "+32487654321",
    "0471234567"
  ],
  "reload_interval_minutes": 5
}
```

### Config Fields

| Field | Description |
|-------|-------------|
| `shelly_url` | Base URL of your Shelly device |
| `shelly_method` | HTTP method: `GET` or `POST` |
| `shelly_endpoint` | API endpoint path |
| `shelly_payload` | JSON payload for POST requests |
| `whitelist` | Array of phone numbers to allow |
| `reload_interval_minutes` | How often to reload config (default: 5) |

### Hosting Options

- **NAS**: Most NAS devices can serve static files via HTTP
- **Raspberry Pi**: Simple Python HTTP server or nginx
- **Router**: Some routers support hosting files
- **Any web server**: Apache, nginx, IIS, etc.

## Shelly Pro 1 Configuration

Your Shelly Pro 1 should be configured with:
- Static IP address on your local network
- No authentication (or configure credentials in the app)
- Relay configured for momentary/pulse mode for gate operation

### Shelly API Endpoints Used

**Shelly Pro 1 (Gen2):**
```
POST http://<ip>/rpc/Switch.Set
Content-Type: application/json
{"id":0,"on":true}
```

**Shelly 1 (Gen1) fallback:**
```
GET http://<ip>/relay/0?turn=on
```

## Troubleshooting

### Service Stops Running
- Disable battery optimization for the app
- Settings > Apps > Gate Opener > Battery > Don't optimize

### Calls Not Being Rejected
- Ensure CALL_PHONE permission is granted
- On older Android, this should work via ITelephony reflection

### Gate Not Triggering
- Check Shelly IP address is correct
- Verify phone is connected to WiFi
- Test connection using the Test button
- Check Shelly is accessible from another device

### App Not Starting on Boot
- Ensure RECEIVE_BOOT_COMPLETED permission is granted
- Some phones require manual app start after first install

## Permissions Required

| Permission | Purpose |
|------------|---------|
| READ_PHONE_STATE | Detect incoming calls |
| CALL_PHONE | Reject/end calls |
| INTERNET | Send HTTP requests to Shelly |
| ACCESS_NETWORK_STATE | Check network connectivity |
| WAKE_LOCK | Keep service running |
| RECEIVE_BOOT_COMPLETED | Auto-start on boot |
| READ/WRITE_EXTERNAL_STORAGE | Legacy storage access |

## Project Structure

```
app/src/main/java/com/microprojects/gateopener/
├── MainActivity.java        # UI and settings
├── GateOpenerService.java   # Background service
├── PhoneCallReceiver.java   # Call detection
├── CallRejector.java        # Call rejection via reflection
├── WhitelistManager.java    # Number whitelist
├── ShellyClient.java        # HTTP client for Shelly
├── ActivityLogger.java      # Activity logging
└── BootReceiver.java        # Auto-start on boot
```

## License

This project is provided as-is for personal use.

## Security Note

This app is designed for a dedicated phone on a private network. It uses reflection to access internal telephony APIs which is not recommended for production apps but works well on older Android versions.
