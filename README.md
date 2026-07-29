# Vulcanite

Vulcanite (`mgf`) is a client-side Fabric prerequisite mod and rendering
provider framework for Minecraft 26.x. It exposes stable, pure-Java APIs for
temporal upscaling, NVIDIA-first experimental frame-generation capability
probing, and bounded present hooks while keeping Minecraft, Fabric, Mojang,
LWJGL, and implementation types out of provider public code.

Installing Vulcanite by itself does not change visuals, gameplay, exposure,
color, render scale, frame rate, or UI. With no supported provider selected, the
26.2 adapter keeps the vanilla presentation path allocation-free for provider
images and command recording.

## Current Release

| Item | Value |
|---|---|
| Vulcanite | `1.0.0` |
| Minecraft adapter | `26.2` |
| Fabric Loader | `0.19.3` or newer |
| Java | `25` or newer |
| NVIDIA-first validation target | GeForce RTX Vulkan backend |

This is the first stable provider API release. The player JAR is still
Minecraft-drop-specific: the 26.2 adapter is not a 26.3-compatible binary.

## Artifacts

| Audience | Coordinate or file | Purpose |
|---|---|---|
| Provider developers | `dev.mgf:mgf-api:1.0.0` | Stable, dependency-free Java contracts |
| Fabric development/runtime | `dev.mgf:mgf-fabric-26.2:1.0.0` | Minecraft 26.2 adapter |
| Players and modpacks | `mgf-1.0.0+mc26.2.jar` | Installable client mod with embedded API |

Downstream providers compile against `mgf-api` only and declare the
`mgf:providers` Fabric entrypoint. DLSS 2, NVIDIA Image Scaling or other 2x
super-resolution, FSR, XeSS, optical-flow, Frame Generation SDKs, and vendor
native binaries belong in independent provider mods.

## Stable 1.0 API Surface

- `UpscalerProvider` can declare render-scale ranges, explicit supported render
  scales, quality modes, color encodings, and required frame resources.
- `UpscaleResources` carries color input/output plus optional depth, motion
  vectors, exposure, reactive mask, transparency mask, UI mask, and the matching
  depth/motion-vector conventions when those images are verified.
- `UpscaleParameters` carries quality mode, jitter sample, and temporal hints
  for reset, mip bias, sharpness, exposure mode, and UI composition.
- `ProviderEnvironment` reports backend, available resources, Vulkan handles,
  multi-present support, and a pure-Java GPU vendor classification.
- `FrameGenerationCapabilities` includes `NVIDIA_EXPERIMENTAL` mode for
  NVIDIA-only provider experiments. The 26.2 adapter keeps it gated by explicit
  config, NVIDIA adapter detection, verified resources, and safe multi-present.
- Stable unsupported reason codes include `backend_not_vulkan`,
  `render_scale_unsupported`, `depth_unavailable`,
  `motion_vectors_unavailable`, `matrices_unavailable`,
  `ui_composition_unavailable`, and `multi_present_unsupported`.

The current 26.2 integration point verifies native SRGB color and SDR identity
exposure metadata. It does not fabricate low-resolution scene color, depth,
motion vectors, matrices, masks, or safe multi-present. Providers that require
unverified resources remain registered and receive deterministic
`UNSUPPORTED` diagnostics instead of partial descriptors.

## Installation

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Place `mgf-1.0.0+mc26.2.jar` in the client `mods` directory.
3. Install a provider mod that depends on Vulcanite.
4. Select Minecraft's Vulkan backend when the provider requires Vulkan.

Provider selection can be configured in `config/mgf-providers.properties`.
Frame Generation experiments require `experimental_frame_generation=true`; the
default player configuration leaves Frame Generation unsupported.

## Documentation

- [Developer documentation](docs/README.md)
- [Getting started](docs/getting-started.md)
- [Provider SPI reference](docs/provider-spi.md)
- [Temporal upscaling contracts](docs/temporal-upscaling.md)
- [Provider conformance](docs/provider-conformance.md)
- [Resource and lifecycle contract](docs/resource-lifecycle.md)
- [Migration from 0.3 to 1.0](docs/migration-0.3-to-1.0.md)
- [Compile-tested minimal provider](docs/examples/minimal-provider.md)
- [Compute synchronization](docs/compute-synchronization.md)

The compile-tested provider example is available in
[`samples/sample-provider`](samples/sample-provider). Provider projects should
depend on `dev.mgf:mgf-api:1.0.0`, not on Minecraft implementation classes.

## Build and Verify

Requires JDK 25.

```text
./gradlew clean build apiCompatibilityCheck publishAllPublicationsToStagingRepository
./gradlew :samples:sample-provider:compileJava
./gradlew :smoke-test:smokeTest
./gradlew :smoke-test:smokeTest -PsmokeProviders
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=requires-all-temporal-upscaling-inputs
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=passthrough
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=recoverable
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=fatal
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl
./gradlew :smoke-test:smokeTest -PwithSodium
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl -PwithSodium
./gradlew :smoke-test:smokeTest -PsmokeValidation -PsmokeProviderMode=passthrough
```

Runtime checks are documented in [smoke-test/README.md](smoke-test/README.md).
The player JAR must not contain sample providers, visual effects, NVIDIA SDKs,
or native vendor binaries.

## License

The stable `mgf-api` artifact is MIT licensed. The player mod and
version-specific implementation use PolyForm Shield 1.0.0. See [LICENSE](LICENSE)
and [mgf-api/LICENSE](mgf-api/LICENSE).
