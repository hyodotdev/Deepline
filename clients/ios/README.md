# iOS Client

Native SwiftUI client scaffold for Deepline.

## What Exists

- XcodeGen project spec at `project.yml`
- generated Xcode project `DeeplineIOS.xcodeproj`
- SwiftUI app shell with:
  - welcome
  - local identity setup
  - chat list
  - 1:1 chat room
  - add friend
  - settings
  - devices / safety placeholder
- Deepline blue light/dark theme
- production-blocked crypto gate placeholder

## Build

Generate the project if needed, then build for Simulator.

```bash
xcodegen generate
xcodebuild -project DeeplineIOS.xcodeproj -scheme DeeplineIOS -configuration Debug -destination 'generic/platform=iOS Simulator' build
```

## Still Missing For Production

- Keychain-backed secret storage
- official Signal Swift bridge
- MLS bridge
- websocket reconnect behavior
- APNs wake-up handling
