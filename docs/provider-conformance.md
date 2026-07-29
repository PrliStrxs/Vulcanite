# Provider Conformance

Vulcanite includes development-only diagnostic provider modes in
`samples/sample-interop` and `smoke-test` so provider authors can check the
adapter's selection behavior.

## Smoke Commands

```powershell
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviders
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviderMode=requires-color
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviderMode=requires-depth
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviderMode=requires-motion-vectors
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviderMode=requires-matrices
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviderMode=requires-depth-motion-matrices
.\gradlew.bat :smoke-test:smokeTest -PsmokeProviderMode=requires-all-temporal-upscaling-inputs
```

`requires-color` is expected to become `ACTIVE` on the Vulkan path. Modes that
require depth, motion vectors, matrices, masks, or UI composition are expected
to remain `UNSUPPORTED` on the current 26.2 adapter with stable reason codes.

## Expected Reason Codes

| Requirement | Current 26.2 result |
|---|---|
| OpenGL backend | `backend_not_vulkan` |
| Native scale unsupported | `render_scale_unsupported` |
| Depth | `depth_unavailable` |
| Motion vectors | `motion_vectors_unavailable` |
| Matrices | `matrices_unavailable` |
| Exposure image/metadata missing | `exposure_unavailable` |
| Reactive mask | `reactive_mask_unavailable` |
| Transparency mask | `transparency_mask_unavailable` |
| UI composition/mask | `ui_composition_unavailable` |
| Frame Generation multi-present | `multi_present_unsupported` |
| Provider exception | `provider_exception` |
| Repeated recoverable failures | `provider_recoverable_failures_exceeded` |

## Provider Checklist

- Probe backend and SDK availability before returning supported.
- Declare every required `FrameResourceKind`.
- Accept `renderScale=1.0` if the provider can operate as a native pass-through
  or sharpening/super-resolution preparation path.
- Treat missing optional resources as lower-quality mode, not as an exception.
- Verify `BorrowedImage` dimensions, format, ownership, lifetime, state, device
  generation, and resource generation inside each callback.
- Return `SKIPPED` for expected no-output conditions.
- Return recoverable or fatal `ProviderResult` values with stable reason codes
  instead of throwing for expected SDK states.
