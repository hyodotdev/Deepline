# SECURITY_BLOCKERS

As of April 15, 2026, Deepline still has real production blockers even after moving away from the Expo-only prototype.

## 1. Official Signal client integration

- Android should use the official Signal Java/Android runtime.
- iOS should use the official Swift bindings.
- The shared KMP layer must never replace Double Ratchet with custom code.

Until those native bridges are integrated, Deepline cannot claim production-grade 1:1 E2EE.

## 2. MLS group implementation maturity

- Group messaging should be implemented with a maintained MLS runtime, ideally OpenMLS through a native bridge.
- Mobile-target maturity and operational hardening still need to be validated for Deepline's concrete Android/iOS builds.

Until that lands, production group chat must remain gated or disabled.

## 3. Production data plane and abuse controls

- The Ktor service now has a JDBC/Flyway-backed Postgres store path, Redis-backed rate limiter mode, and encrypted blob upload sessions with local blob persistence.
- Multi-instance websocket fanout, production object-store hardening, and push transport metadata minimization are still incomplete.
- Edge DDoS mitigation still belongs at the CDN/WAF/load-balancer layer, not only in application code.
- Push transport metadata minimization, abuse tooling, and audit logging need dedicated implementation.

## Current Safe Claim

The repository is being rebuilt toward a production-safe architecture.
It is **not yet safe to market as a production secure messenger** until the blockers above are closed with platform-native crypto, hardened storage, and audited operational controls.
