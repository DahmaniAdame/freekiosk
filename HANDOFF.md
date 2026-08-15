# FreeKiosk development handoff

## Repository

- Working tree: `/root/android-apps/freekiosk`
- Branch: `main`
- Origin: `https://github.com/DahmaniAdame/freekiosk.git`
- Upstream: `https://github.com/RushB-fr/freekiosk.git`
- Android package: `com.freekiosk`
- Device Admin component: `com.freekiosk/.DeviceAdminReceiver`
- Current source version: `1.2.53` (`versionCode 77`)

Preserve the current implementation. Before changing anything, inspect `git status`
and the latest commit. Do not reset, clean, check out, or overwrite user work.

## Device inventory

- `192.168.0.37:5555` — Samsung 75WAF interactive display.
- `192.168.0.53:5555` — Test Galaxy S8+.

Always use the explicit serial with `adb -s IP:5555` so commands cannot target the
other connected device.

## Latest artifacts

The APK files are intentionally ignored by Git. The current WAF artifact is:

- `FreeKiosk-v1.2.53-arm64.apk`
  - SHA-256: `6b173c1173231bad42e65c139db91b7fd1b8afc3bb67a44b108f6ef31766c7ae`
  - 24,873,698 bytes; ARM64-only release. Preserves the kiosk's automatic-timeout protection while allowing a deliberate short power-button press to turn the display off until the next short press, without exposing another launcher or leaving strict lock task.

- `FreeKiosk-v1.2.52-arm64.apk`
  - SHA-256: `b330cb5b1dbde7e3a1edf6dbddded7e92e07e97505b3d10c290b01268631774b`
  - 24,873,698 bytes; ARM64-only release. Keeps exactly one visible half-size square Close All control throughout external-app sessions, including Disney+ playback, while preserving the invisible bottom-right admin multi-tap target.

- `FreeKiosk-v1.2.51-arm64.apk`
  - SHA-256: `f66240022a80118c05bb3abfb0915e8666e526945fef5a3d00b3376d79db9464`
  - 24,873,698 bytes; ARM64-only release. Replaces duplicate close controls with one half-size square Close All button at the absolute bottom-right and terminates every configured allowed app before returning to the kiosk grid.

- `FreeKiosk-v1.2.50-arm64.apk`
  - SHA-256: `2177fd48b6421e6b329461d8c78ff2035219224d36530121d2fc010fcfcfb34a`
  - 24,873,698 bytes; ARM64-only release. Keeps the display awake across external child apps, prevents the monitor from repeatedly restarting Disney+/Netflix playback activities, and reduces the WAF edge gesture guards from 40 dp to 12 dp.

- `FreeKiosk-v1.2.49-arm64.apk`
  - SHA-256: `b417f54d5db3966372ee747aee499481ecbec3ea79097adf4901318199b5e818`
  - 24,873,698 bytes; ARM64-only release. Owns all four WAF display edges with accessibility-layer gesture guards for the complete saved kiosk lifecycle, disables the firmware's freeform/multi-window/side-button switches, and repairs any minimize, finish, or multi-window attempt back to strict fullscreen lock task.
- `FreeKiosk-v1.2.48-arm64.apk`
  - SHA-256: `c11dda82769479bf7f3682e0f9aef6df92800e7e58098c696d0afedf5a5b1736`
  - 24,873,698 bytes; ARM64-only release. Adds accessibility-layer top and bottom edge shields above the 75WAF SystemUI windows while an external child app is active, plus accessibility-layer mirrors of the mandatory close and admin tap controls so app SurfaceViews cannot eclipse them.
- `FreeKiosk-v1.2.47-arm64.apk`
  - SHA-256: `6f279a68c484b23cce1da9ab695159328dcde2b2314f310244f8238f255c301d`
  - 24,873,698 bytes; ARM64-only release. Samsung 75WAF top-status and bottom-navigation edge gestures are disabled through the firmware's native policy switches and continuously reconciled while child-facing.
- `FreeKiosk-v1.2.46-arm64.apk`
  - SHA-256: `485d30cf9369a703a0374b62422ea2003616bf80fc0147464712f8d7a1ce90a5`
  - 24,873,698 bytes; ARM64-only release. The external-app close button is now mandatory and session-persistent, a bottom-edge gesture shield prevents navigation-bar reveal, transient overlay/SystemUI accessibility events cannot remove the button or inject Back into the child app, system navigation keys are consumed while a child session is active, and unexpected Back returns restore the selected app without stopping the overlay in multi-app mode.
- `FreeKiosk-v1.2.45-arm64.apk`
  - SHA-256: `12bfa1a4944d685a02bfac1d51a1b1e9a84c3871d13a203bc66120f067bae642`
  - 24,873,542 bytes; ARM64-only release. Entering the admin flow from Disney+ now creates a native admin session, disarms every external-app relaunch path, and keeps authenticated Settings in the foreground while strict lock task and system-surface blocking remain active.
- `FreeKiosk-v1.2.44-arm64.apk`
  - SHA-256: `18580e490f4f003ae155670493371da687a510e7d629823a0339f7ac064ae30b`
  - 24,873,122 bytes; ARM64-only release; installed and live-tested on the SM-X800 Android 16 tablet. Trusted ADB host authorization survived reboot without re-pairing, wireless debugging remained enabled with Lock Mode both off and on, and the kiosk rebooted into strict `LOCKED` state on the FreeKiosk grid instead of relaunching stale Disney+ state.
  - Stock Android resets the legacy adbd TCP listener itself across reboot, so fixed port 5555 still requires `adb tcpip 5555` after connecting through the persistent paired wireless-debug service. FreeKiosk keeps that paired service enabled and its host authorization non-expiring; it cannot write Android's protected `service.adb.tcp.port` system property.
- `FreeKiosk-v1.2.43-arm64.apk`
  - SHA-256: `ba6a35124e6595e743e4b615c045cec7dd8811faaa7b026e29e6c2b395483a41`
  - 24,873,078 bytes; ARM64-only release with strict fail-closed external-app recovery and the independent boot-persistent wireless-debugging policy.
- `FreeKiosk-v1.2.42-arm64.apk`
  - SHA-256: `7a5651de5f710448d94c154d7d5fae3d60b9ddbb10358ddb0c8e0d20cd4796c9`
  - 24,870,890 bytes; ARM64-only release, pending install/device verification on the SM-X800 Android 16 tablet.
- `FreeKiosk-v1.2.41-arm64.apk`
  - SHA-256: `2951cd7021f63796f485e3fb3f2294fa9188b1236d883ae5704d82c93da4e181`
  - 24,872,658 bytes; ARM64-only release, built, installed, and security-tested on the SM-X800 Android 16 tablet.
- `FreeKiosk-v1.2.39-arm64.apk`
  - SHA-256: `b960506c2308fc335151eb64d57fa37c0c03429314c3358f3447d95211f8c835`
  - 24,872,554 bytes; ARM64-only release, built, installed, and live-tested on the WAF display.
- `FreeKiosk-v1.2.38-arm64.apk`
  - SHA-256: `79d3538b6619803df949a391b3f4ac3f74d9e5713a98f8c0eabd516afddc863f`
  - 24,872,234 bytes; ARM64-only release, built, installed, and route-lifecycle-tested on the WAF display.
- `FreeKiosk-v1.2.34-arm64.apk`
  - SHA-256: `26ba2152e17644b758b10e292df489521255207e8b6450c2c4719ccbce8acfa5`
  - 24,872,170 bytes; ARM64-only release, built, installed, and lifecycle-tested on the WAF display.

- `FreeKiosk-v1.2.33-arm64.apk`
  - SHA-256: `8d78bd77c337716bbafeb1544534b1fa4725b36a20b16ad88ee454b89f983faa`
  - 24,872,170 bytes; ARM64-only release, built, installed, and live-tested on the WAF display.

- `FreeKiosk-v1.2.31-arm64.apk`
  - SHA-256: `671e66a8f1a5a7ecd07087242267811abb6382e98d8811692160817e3f5e1a7f`
  - 24,537,818 bytes; ARM64-only release, built, installed, and tested on the WAF display.
- `FreeKiosk-v1.2.30-arm64.apk`
  - SHA-256: `91b3f98c7b6c042d078848a137a94b0a3228eca0baf087c74849c0db580182c8`
  - 24,537,718 bytes; ARM64-only release, built, installed, and tested on the SM-X800.
- `FreeKiosk-v1.2.28-arm64.apk`
  - SHA-256: `6d1e1a8452233716517b61ad548955c42018fa723903634b712f9e0e71567942`
  - ARM64-only release, built and installed on the SM-X800 test tablet.
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
- Version 1.2.27+ fails closed: it will not launch an external allowed app unless
  Device Owner lock task is active.
- Version 1.2.28 keeps `OverlayService` alive across brief external-app startup
  activity bounces. This prevents the close button from disappearing while apps
  such as Netflix switch between their launch activities.
- Version 1.2.30 makes **Exit Kiosk Mode** persistently disable Lock Mode, clear the
  Device Owner HOME policy, disable FreeKiosk's dedicated HOME component, stop all
  kiosk protection services, remove the app task, and launch the normal device Home.
  External-app services and auto-launch are gated on Lock Mode so they cannot restart
  after an intentional exit.
- Version 1.2.31 scopes every active Device Owner restriction to Lock Mode. Exiting
  clears lock task and its allowlist, status/navigation-bar restrictions, screen
  capture policy, factory-reset and accessibility restrictions, persistent HOME,
  suspended Samsung updater packages, and the system-update policy. Saved admin
  choices are retained and reapplied when Lock Mode is enabled again.
- Version 1.2.33 adds authenticated typed `GET`/`POST /api/settings`, the ordered
  Volume Up x3 then Volume Down x3 emergency exit, and lifecycle-safe Lock Mode
  re-entry. The emergency exit performs the full restriction cleanup without
  removing Device Owner.
- Version 1.2.33 adds optional kiosk wallpaper support with nine anchors. The bundled
  wallpaper defaults to bottom-center; this WAF currently uses
  `file:///storage/emulated/0/Android/data/com.freekiosk/files/Pictures/wallpaper-2.png`
  at bottom-center.
- Samsung WAF system packages are deliberately not hidden during Lock Mode. Hiding
  `com.xbh.navisetting` crashes the firmware's `com.xbh.launcher` package-change
  receiver; hiding `com.android.launcher3` destabilizes Quickstep/Recents and can
  reboot the display. The old hidden-state recovery path remains for safe upgrades.
- Version 1.2.34 controls the signed WAF side-menu service through its exported native
  controller and lifecycle settings. Both sidebars and every expanded-menu button are
  removed while Lock Mode is active, then the exact prior OEM state is restored on exit.

## Current interactive-display state

- Display IP: `192.168.0.37`
- Model: Samsung WAF interactive display, Android 14 / API 34, ARM64.
- Only Android user `0` exists.
- FreeKiosk 1.2.53 is installed in place and remains the Device Owner.
- The display is left running Spectrum Shooter in strict Device Owner `LOCKED` state. The
  WAF chrome policy is active: density is 577dpi, both OEM side-menu
  settings are `0`, global immersive policy is `immersive.full=*`, and
  `enable_freeform_support`, `multi_cb`, and `side_button` are all `0`.
- The `WRITE_SECURE_SETTINGS` and notification runtime grants are present.
- The managed app grid contains Disney+, Netflix, and Spectrum Shooter.
- The display has zero configured accounts. An obsolete secondary Android user and
  its account data were removed with explicit owner authorization before Device Owner
  provisioning.
- The WAF firmware exposes secure legacy TCP ADB at the fixed endpoint
  `192.168.0.37:5555`. The host RSA key stayed authorized through reboot and the
  endpoint remained reachable while Lock Mode was active. Always pass
  `-s 192.168.0.37:5555`; another Android device may also be connected.
- WAF edge protection is provisioned with `skip_swipe_bottom_top=1`,
  `skip_gesture=1`, and `navigation_bar_gesture_disabled_by_policy=1`. FreeKiosk's
  accessibility service is enabled and its `ACCESS_RESTRICTED_SETTINGS` app-op is
  `allow`; four type-2032 edge shields own the complete display perimeter on the grid,
  PIN, and lock-task recovery paths. While an external app is open, two additional
  type-2032 controls mirror the mandatory close button and admin tap target above WAF
  SystemUI and app SurfaceViews. Android can deny a sideloaded accessibility service
  until this one-time provisioning command is run:
  `adb -s 192.168.0.37:5555 shell cmd appops set com.freekiosk ACCESS_RESTRICTED_SETTINGS allow`.
- WAF screen sharing is intentionally disabled for user 0: the firmware property
  `sys.xbh.screen.sharing=false`, `com.xbh.share` is `disabled-user`/stopped, and no
  process is running. Restore only when requested with
  `adb -s 192.168.0.37:5555 shell pm enable --user 0 com.xbh.share`.
- The normal HOME role is the stable WAF launcher `com.xbh.launcher`. Launcher3 is
  left installed and untouched but is not the active HOME holder.

Do not factory-reset the tablet, delete accounts/users, or remove an existing owner
without explicit user approval.

## Wireless ADB continuation

Use current Android platform-tools from a host that can reach `192.168.0.37`:

```sh
adb connect 192.168.0.37:5555
adb -s 192.168.0.37:5555 get-state
```

This WAF uses secure legacy TCP ADB, not Android's rotating TLS pairing port. Do not
replace the existing authorized host key or toggle the firmware ADB configuration
while FreeKiosk is still being stabilized.

If a future firmware update removes the fixed endpoint, fall back to Android pairing:

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

If the installed version is older than 1.2.33, upgrade without clearing app data:

```sh
adb install -r /path/to/FreeKiosk-v1.2.33-arm64.apk
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

## Verified on the WAF display

1. FreeKiosk remains Device Owner after the in-place 1.2.33 APK update.
2. Enabling Lock Mode produces `LOCKED`, restores the FreeKiosk/Disney+/Netflix
   allowlist, disables the status bar, and sets `policy_control=immersive.full=*`.
3. **Exit Kiosk Mode** returns to Android Home without rebooting: lock task becomes
   `NONE`, the allowlist is empty, `mDisabled1` and `mDisabled2` are zero, immersive
   policy is cleared, and the normal launcher resolves as HOME.
4. Device Owner remains installed after exit, but the active factory-reset and
   accessibility-service restrictions are absent. Re-entering Lock Mode reapplies
   the saved accessibility allowlist.
5. The ordered Volume Up x3, Volume Down x3 kill switch exits to normal Home and
   restores restrictions; a subsequent ADB re-enable returns lock task to `LOCKED`.
6. `wallpaper-2.png` renders bottom-center behind the Disney+/Netflix grid.
7. `GET`/`POST /api/settings` is implemented in FreeKiosk; the management console at
   `/opt/apps/KioskControl` on `192.168.10.77` already calls those routes and required
   no server-side change.
8. Wireless ADB at `192.168.0.37:5555` stayed connected through installs, exit,
   re-entry, and the final locked-state verification.
9. After removing all WAF package-hiding calls, a cleared Android crash buffer remained
   empty through Lock Mode entry and observation. The Launcher3 taskbar window was
   absent; the two OEM `com.xbh.navisetting` SYSTEM_ALERT handles remain visible because
   hiding their package crashes the signed WAF launcher firmware.
10. Version 1.2.49 stayed fullscreen and `LOCKED` after repeated double swipes inward from
    both left and right edges at the top, center, and bottom of the display, on both the
    FreeKiosk grid and Disney+. Repeated top/bottom swipes, Home, Recents, and Back also
    left the same allowed task focused. The custom close button returned Disney+ to the
    locked grid, and five bottom-right taps still opened the PIN route without removing
    the four edge guards.

## WAF side-menu control discovered on 2026-08-08

- The two handles and their expanded button columns are `TYPE_SYSTEM_ALERT` (`type=2003`)
  windows from `com.xbh.navisetting`. FreeKiosk's built-in touch regions are
  `TYPE_APPLICATION_OVERLAY`, one layer below them, so enlarging those regions cannot block
  any button owned by the OEM side menu.
- The signed OEM app has no configurable opacity or window-layer setting. Its window type is
  hardcoded, and modifying/re-signing the system APK would break its `android.uid.system`
  signature relationship.
- The OEM app does expose an exported controller provider:
  `content://com.xbh.navisetting.controller/{hide|show}`. It removes/restores both handles and
  every expanded-menu button without hiding or disabling the package.
- Live ADB verification succeeded:

  ```sh
  adb -s 192.168.0.37:5555 shell content update \
    --uri content://com.xbh.navisetting.controller/hide --bind request:s:kiosk
  # com.xbh.navisetting windows: 0

  adb -s 192.168.0.37:5555 shell content update \
    --uri content://com.xbh.navisetting.controller/show --bind request:s:kiosk
  # com.xbh.navisetting windows: 2
  ```

- `WafSideMenuPolicy.kt` uses this native provider at Lock Mode entry/exit.
  It also preserves and temporarily changes `NAVI_ENABLE_SHOW_KEY` and
  `persist.vendor.xbh.navigation.enable`, which the OEM service reads, so a service restart
  cannot bring the menus back during Lock Mode. Exit restores the exact prior values and asks
  the OEM provider to restore the menus immediately.
- The change was built as 1.2.34, installed in place, and verified without losing Device Owner,
  application data, the wallpaper configuration, managed apps, or ADB access.
- Live lifecycle verification showed zero `com.xbh.navisetting` windows and both OEM settings
  at `0` while Lock Mode was `LOCKED`. The emergency exit then restored Lock Task to `NONE`,
  `persist.vendor.xbh.navigation.enable=1`, `NAVI_ENABLE_SHOW_KEY` is unset, and both normal
  side handles are present.

## WAF Launcher3 taskbar control added on 2026-08-08

- The remaining bottom bar was the privileged `com.android.launcher3` `Taskbar` window
  (`TYPE_NAVIGATION_BAR_PANEL`), not a FreeKiosk view. It remained above application overlays
  and ignored `policy_control=immersive.full=*`, even while Lock Task was `LOCKED`.
- Live tests established the exact configuration boundary: at the stock 480dpi and at 576dpi
  Launcher3 reports `isTaskbarPresent:true`; at 577dpi it reports `isTaskbarPresent:false` and
  removes the Taskbar window. The normal SystemUI navigation window is then present only as a
  hidden zero-visible-region surface under the existing immersive policy.
- `WafTaskbarPolicy.kt` applies 577dpi only on devices exposing the Samsung WAF navigation
  controller, remembers the previous override, and restores it on kiosk exit. It is called by
  the coordinated WAF chrome policy, so every child-facing route/reapply and every
  normal/emergency exit uses the same lifecycle. The app SELinux domain cannot discover
  WindowManagerService through the
  `wm`/`cmd` clients, so the policy uses the already-authorized binder retained by Android's
  `WindowManagerImpl` and the verified Android 14 density transactions directly. The physical
  resolution remains 3840x2160.
- The existing two FreeKiosk touch regions over the side-button coordinates remain active but
  are forced visually transparent on WAF, preventing the guards themselves from appearing as
  replacement buttons after the OEM windows are hidden.
- Final verified build: `FreeKiosk-v1.2.37-arm64.apk` (version code 61), SHA-256
  `a89364df75875c14737f6d19a4fbb5dcef095a2932185c965a4abf1575ee6a64`.
- Lifecycle test passed on-device: the Volume Up x3 / Volume Down x3 emergency exit restored
  physical 480dpi (no override), Lock Task `NONE`, the XBH launcher, both OEM side menus, and
  `persist.vendor.xbh.navigation.enable=1`. Re-enabling Lock Mode through the authenticated ADB
  configuration restored 577dpi, Lock Task `LOCKED`, both OEM settings to `0`, and zero Taskbar
  or `com.xbh.navisetting` windows. A deliberate live change to 576dpi was self-healed back to
  577dpi by the in-app binder policy, proving the app—not ADB—owns the active restriction.

## Coordinated WAF chrome lifecycle added in 1.2.38

- `WafKioskChromePolicy.kt` owns the WAF side menus and bottom navigation/taskbar as one
  child-facing policy. The kiosk grid and allowed external apps hide both; PIN and Settings
  restore both, independently of whether Lock Mode itself is enabled.
- A two-second native reconciliation loop reapplies both controls while the kiosk is
  child-facing. Live fault injection passed: changing the density from 577dpi to 576dpi was
  repaired to 577dpi, and changing both OEM side-menu settings to `1` was repaired to `0`.
- Rapid route transitions now persist and clear the previous immersive/heads-up values
  synchronously, avoiding a stale `immersive.full=*` value after entering admin UI.
- The main window switches between edge-to-edge kiosk layout and fitted admin layout. The
  external-app wallpaper uses the physical screen extent, so hiding the phone-profile
  navigation surface leaves no bar-shaped fallback-color strip.
- On-device lifecycle verification passed with Lock Mode off: the child-facing grid showed
  no side handles, bottom controls, or residual bottom strip at 577dpi with both OEM settings
  at `0`; the PIN route restored 480dpi, both side handles, the bottom gesture bar, OEM values
  `null`/`1`, and cleared immersive/heads-up settings; **Back to Kiosk** hid both again.

## PIN chrome and density relayout fix added in 1.2.39

- Kiosk chrome state is now independent from the native tap-to-settings detector. Kiosk and PIN
  are child-facing and keep both WAF side menus and the Launcher3 taskbar hidden; only the
  authenticated Settings route restores OEM chrome. The native fallback tap detector remains
  active only on Kiosk, never on PIN or Settings.
- WAF's 480dpi/577dpi switch changes Android density without changing the 3840x2160 pixel measure
  specs. Fabric previously retained the old root dp constraint, producing a 4616px-wide logical
  surface after returning to Kiosk. This shifted the two-app grid center from x=1920 to x=2308
  and placed bottom-right controls beyond the physical display. `MainActivity` now refreshes
  React Native display metrics and Fabric root constraints immediately and after WAF relayout.
- Live tests on 1.2.39 passed: PIN remained at 577dpi with both OEM side-menu globals at `0` and
  no side handles visible; a forced 480dpi fault self-healed to 577dpi while the app grid stayed
  exactly centered (Disney+ `[1437,907][1898,1253]`, Netflix
  `[1942,907][2403,1253]`); five bottom-right taps still opened PIN after that density cycle.
- The configured admin PIN was not available for automated verification. One attempted default
  `1234` entry was rejected, leaving four attempts until the normal one-hour attempt reset; the
  authenticated Settings route itself was therefore not entered during this test.

## Verified on the SM-X800 tablet (historical)

1. FreeKiosk remains Device Owner after an in-place APK update.
2. Lock task remains `LOCKED` on the grid and while Netflix is foreground.
3. The status bar is disabled (`mDisabled1=0x7a60000`, `mDisabled2=0x1f`), and a
   notification-shade swipe does not expose the shade.
4. The kiosk home screen shows Disney+ and Netflix without the close-button overlay.
5. Netflix remains foreground after its startup activity transitions.
6. Both `OverlayService` and `KioskWatchdogService` remain foreground services while
   Netflix is active. The 136x136 close-button overlay remained at
   `[21,1493][157,1629]` across repeated checks.
7. The close button returns to the app grid, removes `OverlayService`, and leaves lock
   task active.
8. Home and Recents remain blocked while Netflix is active; no launcher, Recents, or
   Settings activity becomes foreground.
