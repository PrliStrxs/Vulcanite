# Vulcanite Provider Documentation

This documentation targets provider authors using Vulcanite 1.0.0.

1. [Getting started](getting-started.md): dependency, metadata, provider
   registration, selection, and diagnostics.
2. [Provider SPI](provider-spi.md): upscaler, Frame Generation capability mode,
   PresentHook, results, failure policy, and stable reason codes.
3. [Temporal upscaling](temporal-upscaling.md): render scale, color, depth,
   motion vectors, matrices, jitter, exposure, masks, and UI composition.
4. [Provider conformance](provider-conformance.md): diagnostic provider modes
   and expected `ACTIVE` or `UNSUPPORTED` outcomes.
5. [Resource lifecycle](resource-lifecycle.md): borrowed handles, command
   recording, synchronization, resize/reset, and shutdown rules.
6. [Minimal provider](examples/minimal-provider.md): a compile-tested skeleton
   that depends only on `mgf-api` and Fabric Loader.
7. [Migration from 0.3 to 1.0](migration-0.3-to-1.0.md): API additions and
   updated provider expectations.
8. [Publishing](publishing.md): local Maven publications, Javadocs, sources, and
   player artifacts.

The API Javadocs are built with `./gradlew :mgf-api:javadoc`. The stable API is
pure Java; Minecraft 26.2 adaptation, Vulkan synchronization, and all Mojang
integration remain owned by the installed `mgf-fabric-26.2` mod.
