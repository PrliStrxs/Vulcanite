# smoke-test

Launch-and-assert harness (design doc §11). The `mgf-smoke` mod waits for the
initial shader load, runs pre-reload probes, triggers and awaits a second
resource reload, runs the final assertion set, writes
`run/mgf-smoke-result.txt` (first line `PASS`/`FAIL`, then one line per check),
and stops the client. A shutdown hook separately writes
`run/mgf-smoke-shutdown-result.txt` so session closure is checked after MGF's
client shutdown callback. The Gradle task fails unless both files say `PASS`.

## Usage

```
./gradlew :smoke-test:smokeTest                      # Vulkan (default)
./gradlew :smoke-test:smokeTest -PsmokeProviders    # Vulkan diagnostic providers
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=passthrough
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=recoverable
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=fatal
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl
./gradlew :smoke-test:smokeTest -PwithSodium         # Sodium coexistence run
./gradlew :smoke-test:smokeTest -PsmokeValidation -PsmokeProviderMode=passthrough
```

The task forces the requested backend with the `--graphicsBackend` launch
argument and passes it to the mod through `-Dmgf.smoke.expectedBackend`, so a
silent vanilla fallback to the other backend fails the run by design. After
initial startup, the harness triggers a second resource reload and verifies that
the registered M3 pipeline is compiled again and the M4 compute objects still
produce the expected result.

`-PsmokeProviders` enables one development-only diagnostic provider for each
role. The 26.2 surface does not advertise safe multi-present behavior, so the
Frame Generation provider remains registered but unsupported. The Upscaler and
PresentHook count open, frame, initial-size, reset, present-receipt, and close
callbacks; every invoked frame callback returns `SKIPPED`. The harness expects
one real present per active frame while internal copies, output copies,
and extra presents remain zero.

`-PsmokeProviderMode=passthrough` records a native-size Vulkan copy from the
borrowed input into the MGF-owned output and returns `SUCCESS`. The output is
pixel-identical, but this exercises provider barriers, output writeback, reload,
and shutdown. `recoverable` injects one recoverable result after a successful
frame and verifies same-frame fallback followed by recovery. `fatal` injects one
fatal result after a successful frame and verifies Upscaler disable while the
PresentHook and one-real-present path continue.

`-PsmokeValidation` is meaningful for Vulkan runs. It adds
`--vulkanValidation`, requires the log to confirm `Enabling Vulkan validation
layers`, and fails if `latest.log` contains a Vulkan `VUID-`, `SYNC-HAZARD-`,
`UNASSIGNED-`, `Validation Error`, or `Validation Warning` report. The result
file also records `validationRequested=true`.

## What is asserted

- Vulkan runs: backend/tier/negotiation flags, requested extension enabled and
  visible through caps, `onDeviceCreated` fired with the correct
  missing-required set (a deliberately nonexistent required extension), all
  VkInterop handles non-zero, compute service available, deterministic storage
  buffer dispatch/readback before and after reload, and a 64-dispatch barrier
  stress result with zero mismatches.
- OpenGL runs: clean degradation — OPENGL_COMPAT tier, empty interop, no
  callback fired, compute unavailable, all provider roles reported as
  `UNSUPPORTED` with reason `vulkan_required`, and all Provider GPU/lifecycle
  counters zero.
- Provider-free Vulkan runs: registered providers remain inactive, while image
  allocations, command recordings, internal copies, output copies, and
  extra presents all remain zero.
- Diagnostic-provider Vulkan runs: Upscaler and PresentHook are active; Frame
  Generation is `UNSUPPORTED/multi_present_unsupported`; the active sessions
  receive initial-size and `RESOURCE_RELOAD` reset callbacks and close once;
  each active frame has exactly one real present and no internal, output-changing,
  or extra present work.
- Passthrough-provider Vulkan runs: the successful provider output is copied
  back on every successful frame; recoverable and fatal injections preserve the
  same-frame real output and the expected runtime selection state.
- All runs: the shader-reload hook fired and the M3 world-geometry pipeline
  compiled successfully through the active backend.
- Sodium runs: the Vulkan checks above pass while Sodium is loaded.

The automated client remains on the title screen, so the level frame graph does
not execute. It does not validate in-world frame resources, a real vendor
provider, interactive window resize, world transitions, or a generated/real
presentation batch. Provider success and injected failure fallback are covered
on the title-screen final-composite seam; downstream providers still need
in-world validation for their supported paths.
See
[`docs/compute-synchronization.md`](../docs/compute-synchronization.md) for the
required ownership and barrier contract.

Run it against every new Minecraft snapshot before declaring MGF compatible
(release gating, design doc §11).

The recorded 0.3 Alpha runtime matrix uses an NVIDIA GeForce RTX 4060 with
NVIDIA 610.62. AMD and Intel runtime behavior has not been validated.
