# Migration from 0.3 to 1.0

Vulcanite 1.0 keeps the provider model from 0.3 and expands the temporal
upscaling contract. 0.3 providers that only require `COLOR` and accept native
scale can continue to compile through compatibility constructors while they
adopt the new metadata.

## API Additions

- `GraphicsAdapterVendor`
- `ProviderEnvironment.adapterVendor()`
- `UpscalerCapabilities.renderScales()`
- `UpscaleResources.uiMask()`
- `UpscaleResources.depthConvention()`
- `UpscaleResources.motionVectorConvention()`
- `UpscaleParameters.jitter()`
- `UpscaleParameters.temporalHints()`
- `DepthConvention`
- `MotionVectorConvention`
- `MotionVectorUnits`
- `MotionVectorDirection`
- `MotionVectorYAxis`
- `JitterSequence`
- `TemporalUpscalingHints`
- `ExposureMode`
- `UiCompositionHint`
- `FrameGenerationMode`
- `FrameGenerationCapabilities.mode()`

## Behavior Changes

- OpenGL provider roles now report `backend_not_vulkan`.
- Missing required resources report resource-specific reason codes instead of a
  generic missing-resource message.
- Upscalers that cannot accept the currently verified native `1.0` adapter path
  report `render_scale_unsupported`.
- `FrameGenerationMode.NVIDIA_EXPERIMENTAL` is gated by
  `experimental_frame_generation=true`, NVIDIA adapter classification, and safe
  multi-present.
- The 26.2 adapter reports SDR identity exposure metadata but does not expose an
  exposure image.

## Source Migration

Old 0.3 construction still works:

```java
new UpscalerCapabilities(0.5, 1.0, Set.of(ColorEncoding.SRGB), Set.of("native"));
new UpscaleParameters(Optional.empty(), "native");
```

New 1.0 providers should declare explicit render scales and inspect temporal
hints:

```java
new UpscalerCapabilities(
        0.5,
        1.0,
        Set.of(0.5, 2.0 / 3.0, 0.75, 1.0),
        Set.of(ColorEncoding.SRGB),
        Set.of("quality", "balanced", "performance"));
```

## Non-goals

1.0 does not bundle DLSS, FSR, XeSS, NVIDIA Streamline, optical-flow SDKs,
Frame Generation native binaries, ray tracing scene extraction, BLAS/TLAS,
denoisers, or visual presets. Those belong in provider mods or later API
milestones.
