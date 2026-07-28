# Provider SPI

All stable provider contracts live under `dev.mgf.api.provider`,
`dev.mgf.api.upscale`, `dev.mgf.api.framegen`, and `dev.mgf.api.present`.
Signatures contain only Java types and opaque numeric native handles.

## Common Contract

Every provider exposes a `ProviderDescriptor` with a validated `namespace:path`
ID, display name, provider version, selection priority, and minimum API version.
For Vulcanite 0.3, use minimum API major `0` and minor `3`.

`probe(...)` reports support before `open(...)` is called. A supported role must
state capabilities and required/optional `FrameResourceKind` values. Never claim
support when a required SDK feature or frame resource is unavailable.

Sessions are created in this order:

```text
Upscaler -> Frame Generation -> PresentHook
```

They are closed in reverse order. Probe, open, resize, reset, frame callbacks,
and close run on Minecraft's render thread.

## Upscaler

`UpscalerProvider.probe` declares scale limits, encodings, quality modes, and
resource requirements. `UpscalerSession.record` receives an `UpscaleFrame` with
the real input color, an MGF-owned output, dimensions, timing, matrices, optional
temporal resources, parameters, and an already-recording command buffer.

Write only to the supplied output. Return `SUCCESS` only when it is valid for
the current frame. On any other result, Vulcanite presents the untouched real
Minecraft image.

## Frame Generation

`FrameGenerationProvider.probe` receives the selected upscaler ID. Capabilities
declare accepted upscalers and a maximum generated-frame count; 0.3 permits at
most one generated frame per real frame.

`FrameGenerationSession.record` receives current and previous real images,
an MGF-owned generated output, timing, matrices, and optional depth, motion,
optical-flow, or UI-mask inputs. Vulcanite never fabricates missing temporal
data. Success forms `[GENERATED, REAL]`; skip or failure forms `[REAL]`.

## PresentHook

`PresentHookSession.beforePresent` runs before each MGF-owned final blit and
present. `afterPresent` receives the result receipt. PresentHook is intended for
vendor pacing, latency markers, telemetry, and present metadata.

The hook must not acquire, blit, submit, present, retain the frame, or take
swapchain ownership. A PresentHook failure disables only that hook; the real
frame continues through Vulcanite's normal present path.

## Results and Failure Policy

Frame callbacks return `ProviderResult`:

| Code | Meaning | Runtime action |
|---|---|---|
| `SUCCESS` | Provider output is valid | Use the output |
| `SKIPPED` | Expected no-output condition | Use the real frame; no strike |
| `RECOVERABLE_FAILURE` | Current output is invalid | Fall back this frame; add a strike |
| `FATAL_FAILURE` | Session cannot continue | Disable the role for this device session |

Success clears recoverable strikes. Three consecutive recoverable failures
disable the role. Exceptions become fatal `provider_exception` results and do
not escape into Minecraft. An upscaler failure skips dependent Frame Generation;
unrelated roles continue when their declared compatibility permits it.

Non-success results require a stable short reason code and a human-readable
message. Do not use exceptions for expected unsupported or skipped conditions.

## Minecraft 26.2 Input Limits

The 26.2 adapter currently reports the fully composed main render target at
native display size. It does not separate world and UI rendering and does not
provide motion vectors, optical flow, UI masks, reactive masks, or a separate
low-resolution scene image. Providers requiring those resources remain visible
but unsupported. Future Minecraft drops receive separate adapter modules while
the pure-Java SPI remains stable.
