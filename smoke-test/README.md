# smoke-test (placeholder)

Launch-and-assert harness, scheduled for milestone M1 (design doc §11):

- Auto-launch the client with MGF + samples on both backends.
- Assert each seam engaged: negotiated extensions present in
  `DeviceInfo.underlyingExtensions`, interop handles non-null, injected passes
  executed (from M2 on).
- Run against every 26.x snapshot the week it drops; release gating — never
  publish an MGF build for an MC version this harness has not passed on.

Not yet wired into `settings.gradle`; add `include "smoke-test"` once the
harness lands.
