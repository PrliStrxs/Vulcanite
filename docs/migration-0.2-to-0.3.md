# Migration from 0.2 to 0.3

Vulcanite 0.3 changes the product from a visible compute demonstration into a
neutral prerequisite and provider framework. It is an Alpha release and permits
breaking changes in `dev.mgf.api.unstable.*`.

## Required Changes

| 0.2 API or behavior | 0.3 replacement |
|---|---|
| `ComputeEffects.registerMainColorAutoExposure(...)` | Removed. Implement an independent `UpscalerProvider`, `FrameGenerationProvider`, or PresentHook provider as appropriate. Vulcanite ships no automatic visual effect. |
| `SampleAutoExposure` and default sample vignette | Removed from default initialization. Development diagnostic providers return `SKIPPED` and do not alter pixels. |
| Direct effect-specific shutdown through `ComputeAutoExposureRegistry` | Removed. Provider sessions receive reset/resize and reverse-order `close()` lifecycle callbacks. |
| Version-specific final-frame integration in consumer code | Register through `mgf:providers`; `mgf-fabric-26.2` owns final blit/present adaptation and synchronization. |
| Consumer dependency on the implementation for new provider contracts | Depend on `dev.mgf:mgf-api:0.3.0-alpha.1` only. |

## APIs Retained as Unstable

The generic `ComputeServices`/`ComputeDispatcher`, frame-graph events, pipeline
helpers, shader source helpers, and PostFx registration remain in the 26.2
implementation artifact under `dev.mgf.api.unstable.*`. They are not part of the
stable provider ABI and may change with each Minecraft drop. The framework no
longer registers a built-in user-visible effect through them.

The M0 capability, extension negotiation, and `VkInterop` APIs in `mgf-api`
remain available. New providers should prefer `ProviderEnvironment` and the
role-specific frame/session contracts for provider lifecycle work.

## Migration Steps

1. Replace the implementation dependency with `mgf-api` for stable provider code.
2. Implement `MgfProviderRegistrar` and add the `mgf:providers` entrypoint.
3. Split device support checks into `probe(...)`; do not allocate during registration.
4. Declare required and optional frame resources honestly.
5. Move native work into the supplied recording command buffer and output image.
6. Handle every reset reason, resize, failure result, and reverse-order close.
7. Test no-provider, unsupported, `SKIPPED`, failure, reload, resize, and shutdown paths.

The 26.2 adapter exposes only the fully composed native-size image. A 0.2 effect
that depended on world-only color or fabricated motion data cannot be migrated
as supported until those resources are supplied by a compatible integration.
