<p align="right"><a href="README.fa.md">فارسی</a></p>

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Salemzi"/>
</p>

<h1 align="center">Salemzi — سالم‌زی</h1>

<p align="center">Sleep on schedule. Wake up intentionally. Stay hydrated.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/License-MIT-blue"/>
</p>

---

Salemzi is an Android app for people who want to take their sleep seriously. It locks your phone at bedtime, silences it, and cuts off internet access — then makes you prove you're actually awake before the morning alarm stops. A water reminder system keeps hydration consistent throughout the day.

No accounts. No cloud. Everything stays on your device.

---

## Features

### Sleep Lock

Set a bedtime and a wake time. When bedtime arrives, Salemzi activates automatically:

- The phone goes silent and internet access is cut off
- A lock screen takes over — the back button and volume keys are disabled
- A live countdown to wake time is shown, with the date displayed in Jalali (Solar Hijri) for Persian users or Gregorian otherwise
- If the **Draw Over Other Apps** permission is granted, the lock screen appears on top of everything. Without it, a high-priority full-screen notification is used as a fallback.

After half the sleep window has passed, an **Early Exit** button appears. Tapping it requires passing the same challenge as the wake alarm — so it's not a free pass.

### Challenge Modes

The same three modes apply to both early exit and the wake alarm:

- **Simple** — tap a button to confirm you're awake. Good if you just want the schedule enforcement without the friction.
- **Math** — solve a math problem. Gets harder with every wrong answer:
  - *Easy:* single multiplication (e.g. `6 × 7`)
  - *Medium:* multiplication with addition or subtraction (e.g. `(4 × 8) + 19`)
  - *Hard:* product of two multiplications (e.g. `(3 × 6) × (2 × 9)`)
- **Memory** — a random 5-digit sequence is shown for 4 seconds, then hidden. Type it from memory to proceed. A **Retry** button generates a new sequence if needed.

### Wake Alarm

At wake time, an alarm rings and the challenge screen appears:

- The alarm keeps ringing until the challenge is solved — no snooze, no dismiss button
- The alarm sound can be the system default, any ringtone, or an audio file from storage
- The challenge screen is reachable from the notification shade and the Recents switcher, so navigating away doesn't let you escape it
- If the lock screen is already open when wake time hits, no duplicate notification is sent

### Sleep Schedule

- **Scheduled mode:** sleep and wake activate automatically at your set times every day via `AlarmManager`. A reminder notification is sent 15 minutes before bedtime.
- **Manual mode:** start sleep mode immediately with one tap, without waiting for the scheduled time.
- All alarms are restored automatically after a device reboot.

### Water Reminders

- 8 reminders distributed across the day
- Times are calculated around your meal schedule (breakfast, lunch, dinner) for medically sensible hydration windows
- Delivered as an overlay popup (if permission is granted) or a notification
- Reschedules itself every day automatically

---

## Requirements

| | |
|---|---|
| Android | 11 (API 30) or higher |
| `POST_NOTIFICATIONS` | For sleep/wake/water notifications |
| `SCHEDULE_EXACT_ALARM` | For exact bedtime and water reminder scheduling |
| `FOREGROUND_SERVICE` | To keep the alarm running in the background |
| `BIND_VPN_SERVICE` | To cut off internet during sleep |
| `SYSTEM_ALERT_WINDOW` | Optional — enables the lock screen overlay over other apps |

The app walks you through each permission on first launch.

---

## Building

```bash
git clone https://github.com/mehranlatifi83/salemzi.git
cd salemzi
```

Open the project in **Android Studio Hedgehog (2023.1.1) or newer**, then run on a physical device or emulator with Android 11+.

> No API keys, no external services, no configuration files needed.

---

## License

[MIT](LICENSE) © 2026 Mehran Latifi
