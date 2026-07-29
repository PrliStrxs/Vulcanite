# Provider SPI

All stable provider contracts live under `dev.mgf.api.provider`,
`dev.mgf.api.upscale`, `dev.mgf.api.framegen`, and `dev.mgf.api.present`.
Signatures contain only Java platform types, Vulcanite API types, and opaque
numeric native handles.

## Common Contract

Every provider exposes a `ProviderDescriptor` with a validated `namespace:path`
ID, display name, provider version, selection priority, and minimum API version.
For Vulcanite 1.0, use minimum API major `1` and minor `0` once your provider
requires the new temporal contracts.

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

`UpscalerProvider.probe` declares:

- `minimumScale` and `maximumScale`;
- explicit `renderScales`, such as `0.5`, `0.6666667`, `0.75`, and `1.0`;
- accepted `ColorEncoding` values;
- quality mode strings;
- required and optional `FrameResourceKind` values.

`UpscalerSession.record` receives an `UpscaleFrame` with input/output images,
dimensions, timing, command recorder, resources, quality mode, jitter, and
temporal hints.

The 1.0 resources are:

| Kind | Meaning | Availability rule | Missing reason |
|---|---|---|---|
| `COLOR` | Render-resolution input color | Only when the adapter has a verified color descriptor | `low_res_color_unavailable` |
| `DEPTH` | Depth matching input color | Must include `DepthConvention` | `depth_unavailable` |
| `MOTION_VECTORS` | Motion vectors matching input color | Must include `MotionVectorConvention` | `motion_vectors_unavailable` |
| `MATRICES` | Current/previous camera transforms | Must be coherent with jitter and reset | `matrices_unavailable` |
| `EXPOSURE` | Exposure image resource | Not verified in 26.2; identity exposure is reported through `TemporalUpscalingHints` without enabling this resource | `exposure_unavailable` |
| `REACTIVE_MASK` | Low temporal-trust content | Only when verified per frame | `reactive_mask_unavailable` |
| `TRANSPARENCY_MASK` | Translucent/composition mask | Only when verified per frame | `transparency_mask_unavailable` |
| `UI_MASK` | UI separation/composition mask | Only when UI is native or a mask is verified | `ui_composition_unavailable` |
| `OPTICAL_FLOW` | Provider or adapter optical flow | Core adapter does not provide it | `optical_flow_unavailable` |

Write only to the supplied output. Return `SUCCESS` only when it is valid for
the current frame. On any other result, Vulcanite presents the untouched real
Minecraft image.

## Frame Generation

`FrameGenerationProvider.probe` receives the selected upscaler ID. Capabilities
declare accepted upscalers, color encodings, maximum generated frames, and a
mode:

- `STANDARD`: future non-vendor-specific capability lane.
- `NVIDIA_EXPERIMENTAL`: NVIDIA-only experimental lane for provider mods.

The 26.2 adapter keeps `multiPresentSupported=false`, so Frame Generation stays
`UNSUPPORTED/multi_present_unsupported` even if a provider probes successfully.
An `NVIDIA_EXPERIMENTAL` provider is also gated by
`experimental_frame_generation=true` and adapter vendor classification before
multi-present is considered.

`FrameGenerationSession.record` receives current/previous real images, an
MGF-owned generated output, timing, matrices, and optional depth, motion,
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
