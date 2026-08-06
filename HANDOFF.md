# FreeKiosk development handoff

## Repository

- Working tree: `/root/android-apps/freekiosk`
- Branch: `main`
- Origin: `https://github.com/DahmaniAdame/freekiosk.git`
- Upstream: `https://github.com/RushB-fr/freekiosk.git`
- Android package: `com.freekiosk`
- Device Admin component: `com.freekiosk/.DeviceAdminReceiver`
- Current source version: `1.2.27` (`versionCode 51`)

Preserve the current implementation. Before changing anything, inspect `git status`
and the latest commit. Do not reset, clean, check out, or overwrite user work.

## Latest artifacts

The APK files are intentionally ignored by Git. On the original workspace they are:

- `FreeKiosk-v1.2.27.apk`
  - SHA-256: `cfacffe7c011a27023f1fd4c23c976bec0c574999a50ab9d2f49f5dfabc9856d`
- `LADB-v2.6-local.apk`
  - SHA-256: `911aff76c8ffc8eb08ff8f8443a90f33e697dc06f05daaf4c72804d805a247ca`

If the FreeKiosk APK is unavailable on a new host, install Node.js 20+, restore
dependencies with `npm ci`, and build the Android release variant from the committed
source. The Android build-output directories were previously removed to recover disk
space; they are generated again by Gradle.

## Implemented changes

- Configurable kiosk background color, defaulting to `#333333`.
- FreeKiosk branding in the top-left of kiosk mode hidden at zero opacity.
- Persistent configurable maximum-volume limit while the kiosk is active.
- Automatic centered app grid, alphabetical ordering, and up to six apps per row.
- App dragging disabled in kiosk mode.
- Configurable close-button position. The button is shown only while an allowed app
  is active, remains stable, and closes that app.
- APK self-update through the local management API.
- Settings-change history plus JSON export/import and backup restoration.
- Stronger foreground-app enforcement and protection against unrelated activities.
- Device Owner lock-task enforcement, System UI restrictions, and notification/status
  bar suppression.
- Version 1.2.27 fails closed: it will not launch an external allowed app unless
  Device Owner lock task is active.

## Current blocker

Provision FreeKiosk as Android Device Owner, then test version 1.2.27 on the tablet.

Known tablet state:

- Tablet IP: `192.168.0.153`
- Only Android user `0` exists.
- `android.software.device_admin` is present.
- `android.software.managed_users` is present.
- No administrator is visible in Android Settings.
- Previous provisioning returned `Can't set package ... as device owner.`
- Some owner diagnostics were unavailable through LADB.
- Previous Wireless ADB pairing details have expired.

Do not factory-reset the tablet, delete accounts/users, or remove an existing owner
without explicit user approval.

## Wireless ADB continuation

Use current Android platform-tools from a host that can reach `192.168.0.153`.
Ask the user to open **Developer options > Wireless debugging > Pair device with
pairing code**, then obtain the fresh pairing address and six-digit code.

```sh
adb pair IP:PAIRING_PORT PAIRING_CODE
adb mdns services
```

If mDNS does not reveal the connection endpoint, ask for the IP and port displayed on
the main Wireless debugging screen, then connect:

```sh
adb connect IP:CONNECTION_PORT
adb devices -l
```

Collect the exact diagnostic output:

```sh
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell pm list users
adb shell pm list packages --user 0 com.freekiosk
adb shell dumpsys package com.freekiosk | grep versionName
adb shell settings get secure user_setup_complete
adb shell settings get global device_provisioned
adb shell cmd device_policy list-owners
adb shell dumpsys device_policy
```

Some OEM builds may not support every diagnostic command. Continue with the remaining
commands and retain complete errors.

If the installed version is older than 1.2.27, upgrade without clearing app data:

```sh
adb install -r /path/to/FreeKiosk-v1.2.27.apk
```

Attempt Device Owner provisioning:

```sh
adb shell cmd device_policy set-device-owner --user 0 --device-owner-only com.freekiosk/.DeviceAdminReceiver
```

If `--device-owner-only` is unsupported:

```sh
adb shell cmd device_policy set-device-owner --user 0 com.freekiosk/.DeviceAdminReceiver
```

If it fails, preserve the full error and collect only relevant policy logs:

```sh
adb shell logcat -d -b system -t 1000 | grep -Ei "device.?policy|device owner|profile owner|freekiosk"
```

Diagnose the precise blocker before changing device state.

## Success checks

After Device Owner provisioning succeeds:

1. Confirm FreeKiosk is the Device Owner.
2. Start kiosk mode and verify navigation/status bars cannot remain visible.
3. Verify notifications cannot overlay the kiosk or an allowed app.
4. Verify the kiosk home screen does not show the close button.
5. Launch an allowed app and confirm it remains above the kiosk.
6. Confirm the close button stays visible without pulsing and closes only the active
   allowed app.
7. Confirm the launcher, recent-apps screen, Settings, and unrelated apps cannot appear.
