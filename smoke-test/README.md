# smoke-test

Launch-and-assert harness (design doc §11). The `mgf-smoke` mod waits for the
initial shader load, runs pre-reload probes, triggers and awaits a second
resource reload, runs the final assertion set, writes
`run/mgf-smoke-result.txt` (first line `PASS`/`FAIL`, then one line per check),
and stops the client. The Gradle task fails unless the file says `PASS`.

## Usage

```
./gradlew :smoke-test:smokeTest                      # Vulkan (default)
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl
./gradlew :smoke-test:smokeTest -PwithSodium         # Sodium coexistence run
./gradlew :smoke-test:smokeTest -PsmokeValidation    # Vulkan + Khronos validation
```

The task forces the requested backend with the `--graphicsBackend` launch
argument and passes it to the mod through `-Dmgf.smoke.expectedBackend`, so a
silent vanilla fallback to the other backend fails the run by design. After
initial startup, the harness triggers a second resource reload and verifies that
the registered M3 pipeline is compiled again and the M4 compute objects still
produce the expected result.

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
  callback fired, compute unavailable, and the exact fail-soft reason reported.
- All runs: the shader-reload hook fired and the M3 world-geometry pipeline
  compiled successfully through the active backend.
- Sodium runs: the Vulkan checks above pass while Sodium is loaded.

The automated client remains on the title screen, so the level frame graph does
not execute. It does not validate visible auto exposure, its ordering before M2
PostFx, window resize, world-transition exposure reset, or in-world shutdown.
Run the validation smoke first, then run `:samples:sample-interop:runClient`,
enter a world, and exercise those paths manually before release. See
[`docs/compute-synchronization.md`](../docs/compute-synchronization.md) for the
required ownership and barrier contract.

Run it against every new Minecraft snapshot before declaring MGF compatible
(release gating, design doc §11).
