# smoke-test

Launch-and-assert harness (design doc §11). The `mgf-smoke` mod runs the
assertion set at CLIENT_STARTED, writes `run/mgf-smoke-result.txt`
(first line `PASS`/`FAIL`, then one line per check), and stops the client;
the Gradle task fails unless the file says `PASS`.

## Usage

```
./gradlew :smoke-test:smokeTest                      # Vulkan (default)
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl
./gradlew :smoke-test:smokeTest -PwithSodium         # Sodium coexistence run
```

The task rewrites `preferredGraphicsBackend` in `run/options.txt` before
launching and passes the expected backend to the mod via
`-Dmgf.smoke.expectedBackend`, so assertions know which branch to check
(a silent vanilla auto-fallback to the other backend therefore FAILS the run,
by design). After initial startup, the harness triggers a second resource reload
and verifies that the registered M3 pipeline is compiled again.

## What is asserted

- Vulkan runs: backend/tier/negotiation flags, requested extension enabled and
  visible through caps, `onDeviceCreated` fired with the correct
  missing-required set (a deliberately nonexistent required extension), all
  VkInterop handles non-zero.
- OpenGL runs: clean degradation — OPENGL_COMPAT tier, empty interop, no
  callback fired.
- All runs: the shader-reload hook fired and the M3 world-geometry pipeline
  compiled successfully through the active backend.
- Sodium runs: the Vulkan checks above pass while Sodium is loaded.

Run it against every new Minecraft snapshot before declaring MGF compatible
(release gating, design doc §11).
