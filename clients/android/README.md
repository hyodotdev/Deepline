# Android Client

Native Android client for Deepline local-dev flows.

## What Exists

- `app/` Gradle Android application module wired into the monorepo as `:androidApp`
- Jetpack Compose app with:
  - welcome
  - local identity setup
  - chat list
  - 1:1 chat room
  - add friend
  - settings
  - devices / safety placeholder
- local Ktor server integration for:
  - user registration
  - device registration
  - pre-key bundle publish
  - conversation listing
  - message listing and send
  - invite-code generation and import
- shared model dependency on `:shared`
- Deepline blue light/dark theme
- launch extras for local testing:
  - `deepline.reset_state`
  - `deepline.server_url`

## Build

Build from the repository root.

```bash
./gradlew :androidApp:assembleDebug
```

Run on an emulator with the local backend:

```bash
adb install -r clients/android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.hyo.deepline.android/.MainActivity \
  --ez deepline.reset_state true \
  --es deepline.server_url http://10.0.2.2:9091
```

APK output:

- `clients/android/app/build/outputs/apk/debug/app-debug.apk`

## Still Missing For Production

- secure local storage for private keys
- official Signal runtime bridge
- MLS bridge
- websocket session lifecycle
- push notification wake-up handling
