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
It does not currently expose a separate low-resolution world image, motion
vectors, UI mask, or fabricated temporal inputs. Providers that require missing
resources remain registered but unsupported.

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
./gradlew build
./gradlew check publishAllPublicationsToStagingRepository
./gradlew :samples:sample-provider:compileJava
```

Runtime checks are documented in [smoke-test/README.md](smoke-test/README.md).
The development-only `sample-interop` module can register diagnostic providers
with `-Dmgf.sample.diagnosticProviders=true`; their callbacks return `SKIPPED`
and do not alter output pixels.

## License

The stable `mgf-api` artifact is MIT licensed. The player mod and version-specific
implementation use PolyForm Shield 1.0.0. See [LICENSE](LICENSE) and
[mgf-api/LICENSE](mgf-api/LICENSE).
