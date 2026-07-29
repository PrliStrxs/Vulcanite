# Temporal Upscaling Contracts

Vulcanite 1.0 defines the API surface that DLSS 2, NVIDIA super-resolution,
FSR, XeSS, and AI upscaling provider mods need. The core mod does not ship or
wrap vendor SDKs.

## Render and Display Size

`FrameDimensions` separates render size from display size. Providers declare
supported values through `UpscalerCapabilities.renderScales()`. The 1.0 adapter
recognizes `0.5`, `0.6666667`, `0.75`, and `1.0`; the Minecraft 26.2 live path
currently exposes only verified native `1.0` color input, so providers that do
not accept native scale receive `UNSUPPORTED/render_scale_unsupported`.

## Resources

`UpscaleResources` always contains:

- `inputColor`: adapter-verified color input.
- `outputColor`: MGF-owned output at display resolution.

Optional fields are present only when verified for the current frame:

- `depth` with `DepthConvention`.
- `motionVectors` with `MotionVectorConvention`.
- `exposure` when exposure is an image; SDR identity exposure is reported in
  `TemporalUpscalingHints`.
- `reactiveMask`.
- `transparencyMask`.
- `uiMask`.

If an image is present, its convention metadata is present in the same record.
If the adapter cannot verify a resource, it omits the descriptor and selection
uses a stable reason code.

## Depth Convention

`DepthConvention` describes whether depth is reversed and the valid normalized
range. Providers must not infer reversed-Z or range from format alone.

## Motion-Vector Convention

`MotionVectorConvention` describes:

- units: pixels or normalized render size;
- direction: current-to-previous or previous-to-current;
- Y axis orientation;
- X/Y scale.

Zero motion is `(0, 0)` under the declared convention.

## Jitter and Camera

`UpscaleParameters.jitter()` carries the current jitter sample as a
`JitterSequence`. `UpscaleCameraParameters` remains optional and is supplied
only when matrices, clip planes, field of view, and jitter are coherent for the
same frame. Missing matrices use `UNSUPPORTED/matrices_unavailable` when
required.

## Exposure and UI

`TemporalUpscalingHints.exposureMode()` is one of:

- `IDENTITY`: no exposure transform is applied by the adapter;
- `ADAPTER_METADATA`: a future adapter supplies scalar metadata;
- `PROVIDER_RESOURCE`: an exposure image is present.

`UiCompositionHint` tells providers whether UI is already in input, composed
after upscaling, represented by a mask, or unknown. The 26.2 path currently
reports `UI_ALREADY_IN_INPUT` and does not expose a UI mask.

## Reset and Fallback

`FrameInfo.historyReset()` and `TemporalUpscalingHints.resetHistory()` require
providers to discard temporal history. `resize`, `reset`, resource reload, world
change, provider change, and device replacement must also invalidate SDK state.

If a provider returns `SKIPPED`, `RECOVERABLE_FAILURE`, `FATAL_FAILURE`, or
throws, Vulcanite falls back to the same real Minecraft frame.

## NVIDIA Notes

NVIDIA DLSS 2 or NVIDIA super-resolution providers should probe Vulkan support,
NVIDIA adapter suitability, SDK availability, required resources, and accepted
render scales before returning `available(...)`. NVIDIA Frame Generation should
use `FrameGenerationMode.NVIDIA_EXPERIMENTAL` and expect the 26.2 adapter to
return unsupported unless experimental config, NVIDIA vendor detection, verified
HUD-less/UI/depth/motion resources, and safe multi-present are all satisfied.
