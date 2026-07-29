# Getting Started

## Requirements

- Java 25
- Fabric Loader 0.19.3 or newer
- Minecraft 26.2 for the current adapter
- Vulcanite player mod `mgf-1.0.0+mc26.2.jar`

Provider code compiles against the stable API, not the player implementation:

```groovy
repositories {
    maven { url = uri("path/to/mgf/build/repository") }
    maven { url = "https://maven.fabricmc.net/" }
}

dependencies {
    compileOnly "dev.mgf:mgf-api:1.0.0"
    compileOnly "net.fabricmc:fabric-loader:0.19.3"
}
```

The local repository is produced by `publishAllPublicationsToStagingRepository`.
Do not add `mgf-fabric-26.2`, Minecraft, Mojang, or LWJGL to provider API source
unless a separate, version-specific integration module intentionally needs them.

## Fabric Registration

Implement `MgfProviderRegistrar` and register supported roles through the
provided `ProviderRegistry`. Add the registrar to `fabric.mod.json`:

```json
{
  "entrypoints": {
    "mgf:providers": [
      "com.example.graphics.ExampleProviders"
    ]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "26.2",
    "java": ">=25",
    "mgf": ">=1.0.0"
  }
}
```

Registration occurs during client initialization. It must be deterministic and
must not probe native SDKs, allocate GPU resources, or start device sessions.
Each registrar is isolated: one failing registrar does not prevent others from
loading.

## Selection

Vulcanite freezes registrations, probes them on the render thread after the
graphics device exists, and selects at most one provider per role. Candidates
are ordered by descending descriptor priority and then lexical provider ID.
Frame Generation is selected after the upscaler; PresentHook is selected after
Frame Generation so compatibility declarations can be enforced.

Users can create `config/mgf-providers.properties`:

```properties
upscaler=auto
frame_generation=off
present_hook=example:present-hook
experimental_frame_generation=false
```

Each provider value is `auto`, `off`, or an exact `namespace:path` provider ID.
Invalid or missing exact IDs fail soft and remain visible in provider
diagnostics. `experimental_frame_generation=true` is required before an
`NVIDIA_EXPERIMENTAL` Frame Generation provider can pass its first gate.

## Runtime Diagnostics

`Mgf.runtime().providers()` returns an immutable snapshot for the three roles,
including the selected provider ID, session state, reason code, and message.
`ProviderEnvironment` reports backend, available resources, Vulkan interop
handles, multi-present support, and adapter vendor classification.

Start from the [compile-tested provider](examples/minimal-provider.md), then read
the [SPI](provider-spi.md), [temporal upscaling](temporal-upscaling.md), and
[resource lifecycle](resource-lifecycle.md) contracts before returning a
supported probe result.
