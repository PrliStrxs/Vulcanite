# Vulcanite Provider Documentation

This documentation targets provider authors using Vulcanite 0.3.0 Alpha 1.

1. [Getting started](getting-started.md): dependency, metadata, registration,
   selection, and diagnostics.
2. [Provider SPI](provider-spi.md): upscaler, frame generation, PresentHook,
   callback results, and failure policy.
3. [Resource lifecycle](resource-lifecycle.md): borrowed handles, command
   recording, synchronization, resize/reset, and shutdown rules.
4. [Minimal provider](examples/minimal-provider.md): a compile-tested skeleton
   that depends only on `mgf-api` and Fabric Loader.
5. [Publishing](publishing.md): local Maven publications, Javadocs, sources, and
   player artifacts.
6. [Migration from 0.2 to 0.3](migration-0.2-to-0.3.md): breaking changes and
   replacements.

The API Javadocs are built with `./gradlew :mgf-api:javadoc`. The stable API is
pure Java; Minecraft 26.2 adaptation and Vulkan synchronization remain owned by
the installed `mgf-fabric-26.2` mod.
