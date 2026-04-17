# TODO Security

- Signal/libsignal integration status:
  Blocked today. Replace the demo provider with official Signal-compatible session handling once an Expo-compatible client runtime exists.
- MLS integration status:
  Blocked today. Keep `GroupCryptoProvider` as the boundary until a production-ready audited MLS runtime is available.
- Sealed sender:
  Minimize sender metadata and add a sealed-sender-compatible transport plan.
- Private contact discovery:
  Replace invite-code-only discovery with privacy-preserving contact discovery.
- Push notification metadata minimization:
  Reduce notification routing leaks and avoid meaningful plaintext previews.
- Key backup and recovery:
  Add encrypted device backup and recovery UX without server access to plaintext keys.
- Device verification:
  Add verified safety-number comparison and explicit trust state per device.
- Multi-device sync:
  Add safe device linking, verified handoff, and per-device fanout.
- Security audit:
  Schedule an external audit before any production release.
- Abuse and spam reporting model:
  Add abuse tooling that does not undermine E2EE guarantees.
