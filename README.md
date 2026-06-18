# Salemzi — سالم‌زی

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Salemzi icon"/>
</p>

<p align="center">
  <strong>A sleep discipline app for Android</strong><br/>
  Lock your phone at bedtime, wake up intentionally, and stay hydrated — all on autopilot.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white" alt="Android 11+"/>
  <img src="https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white" alt="Java"/>
  <img src="https://img.shields.io/badge/UI-Material%20Design%203-6750A4?logo=material-design&logoColor=white" alt="Material 3"/>
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License"/>
</p>

---

## Features

### Sleep Lock
- Automatically activates at your scheduled bedtime
- Locks the screen with a **challenge** (math problem, digit-memory sequence, or simple confirm) so you can't dismiss it carelessly
- Silences the ringer and blocks internet access via a local VPN tunnel during the sleep window
- Keeps the lock screen visible as an overlay over any other app (with overlay permission)

### Wake Alarm
- Rings at your wake time with an alarm sound of your choice
- Shows the challenge screen — the alarm won't stop until the challenge is solved
- Accessible from the notification and the Recents switcher; persists until completed
- Early-exit option available with the challenge as a gate

### Water Reminders
- 8 evenly-spaced reminders throughout the day, timed around your meal schedule for optimal hydration
- Overlay popup or notification depending on permissions
- Reschedules itself automatically every day

### Privacy & Safety
- All data stays on-device — no analytics, no network calls, no account required
- Privacy policy shown on first launch

---

## Requirements

| Item | Value |
|---|---|
| Android | 11 (API 30) or higher |
| Permissions | `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE`, `BIND_VPN_SERVICE`, `SYSTEM_ALERT_WINDOW` (optional) |
| Internet | Not required |

---

## Tech Stack

- **Language:** Java
- **UI:** Material Design 3 (MaterialButton, MaterialTimePicker, BottomNavigationView)
- **Scheduling:** `AlarmManager.setExactAndAllowWhileIdle()`
- **Network blocking:** `VpnService` (local tunnel, no external server)
- **Alarm audio:** `MediaPlayer` with `USAGE_ALARM` audio attribute
- **Persistence:** `SharedPreferences`
- **Calendar:** Custom Jalali (Solar Hijri) calendar utility

---

## Project Structure

```
app/src/main/java/ir/mehranlatifi83/salemzi/
├── manager/
│   ├── ScheduleManager.java        # Alarm scheduling logic
│   └── WaterReminderManager.java   # Water reminder slots
├── receiver/
│   ├── BootReceiver.java           # Restore alarms after reboot
│   ├── SleepScheduleReceiver.java  # Sleep/wake alarm handler
│   └── WaterReminderReceiver.java  # Water reminder handler
├── service/
│   ├── SleepVpnService.java        # Local VPN tunnel for app blocking
│   └── WakeAlarmService.java       # Foreground alarm service
├── ui/
│   ├── MainActivity.java           # Main screen with schedule settings
│   ├── SleepLockActivity.java      # Sleep lock + wake challenge screen
│   ├── WaterActivity.java          # Water reminder settings
│   └── WaterOverlayActivity.java   # Water overlay popup
└── util/
    ├── JalaliCalendar.java
    └── TimePickerHelper.java
```

---

## Building

1. Clone the repo
2. Open in Android Studio (Hedgehog or newer)
3. Run on a device or emulator running Android 11+

No API keys or external configuration required.

---

## Privacy Policy

Available in [English](app/src/main/assets/privacy_policy_en.html) and [Persian](app/src/main/assets/privacy_policy_fa.html).  
Shown automatically on first launch based on device language.

---

## License

[MIT](LICENSE) © 2026 Mehran Latifi
