# Vulcanite

Vulcanite (`mgf`) is a client-side Fabric prerequisite mod and graphics provider
framework for Minecraft 26.x. It exposes stable, pure-Java APIs for upscaling,
frame generation, and controlled present hooks while keeping Minecraft, Fabric,
LWJGL, and version-specific rendering types out of downstream provider code.

Vulcanite does not enable a visual preset by itself. With no supported provider
selected, Minecraft follows its original rendering and presentation path with no
MGF image allocation, command recording, copy, or extra present.

## Artifacts

| Audience | Coordinate or file | Purpose |
|---|---|---|
| Provider developers | `dev.mgf:mgf-api:0.3.0-alpha.1` | Stable, dependency-free Java contracts |
| Fabric development/runtime | `dev.mgf:mgf-fabric-26.2:0.3.0-alpha.1` | Minecraft 26.2 adapter |
| Players and modpacks | `mgf-0.3.0-alpha.1+mc26.2.jar` | Installable client mod with embedded API |

Downstream providers compile against `mgf-api` only and declare the
`mgf:providers` Fabric entrypoint. DLSS, FSR, XeSS, optical-flow, and vendor SDK
integrations belong in independent provider mods.

## Provider Roles

- `UpscalerProvider` records one real-frame upscale into an MGF-owned output.
- `FrameGenerationProvider` may produce at most one generated frame per real frame.
- `PresentHookProvider` receives bounded callbacks around MGF-owned presentation;
  it never owns or invokes swapchain presentation.

The Minecraft 26.2 adapter exposes the fully composed, native-size main target.
It does not currently expose verified camera parameters, a separate
low-resolution world image, depth, motion vectors, UI mask, or fabricated
temporal inputs. Providers that require missing resources remain registered but
unsupported. Because 26.2 does not expose a verified safe multi-present
capability, Frame Generation providers remain registered but are reported as
`UNSUPPORTED/multi_present_unsupported` by this adapter.

## Documentation

- [Developer documentation](docs/README.md)
- [Getting started](docs/getting-started.md)
- [Provider SPI reference](docs/provider-spi.md)
- [Resource and lifecycle contract](docs/resource-lifecycle.md)
- [Publishing artifacts](docs/publishing.md)
- [Migration from 0.2](docs/migration-0.2-to-0.3.md)
- [Compile-tested minimal provider](docs/examples/minimal-provider.md)
- [Compute synchronization](docs/compute-synchronization.md)

## Build and Verify

Requires JDK 25.

```text
./gradlew clean build apiCompatibilityCheck publishAllPublicationsToStagingRepository
./gradlew :samples:sample-provider:compileJava
./gradlew :smoke-test:smokeTest
./gradlew :smoke-test:smokeTest -PsmokeProviders
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=passthrough
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=recoverable
./gradlew :smoke-test:smokeTest -PsmokeProviderMode=fatal
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl
./gradlew :smoke-test:smokeTest -PwithSodium
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl -PwithSodium
./gradlew :smoke-test:smokeTest -PsmokeValidation -PsmokeProviderMode=passthrough
```

Runtime checks are documented in [smoke-test/README.md](smoke-test/README.md).
`-PsmokeProviders` enables the development-only diagnostic providers in
`sample-interop`. Active callbacks exercise selection and lifecycle paths,
return `SKIPPED`, and do not alter output pixels or add a presentation.
The passthrough and injected-failure modes exercise successful Vulkan output
writeback and same-frame recoverable/fatal fallback with pixel-identical data.

The 0.3 Alpha runtime matrix was executed on an NVIDIA GeForce RTX 4060 with
NVIDIA 610.62. AMD and Intel GPUs remain unverified.

## License

The stable `mgf-api` artifact is MIT licensed. The player mod and version-specific
implementation use PolyForm Shield 1.0.0. See [LICENSE](LICENSE) and
[mgf-api/LICENSE](mgf-api/LICENSE).
