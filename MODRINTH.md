# Vulcanite

**A neutral graphics-provider framework for Minecraft's Vulkan transition.**

Vulcanite (`mgf`) is a client-side Fabric prerequisite mod for Minecraft 26.x.
It gives graphics provider mods one shared integration layer for native Vulkan
access, GPU work submission, upscaling, frame-generation contracts, and bounded
presentation callbacks.

> Installing Vulcanite by itself does not change exposure, color, sky, vignette,
> resolution, or frame rate. It does not bundle DLSS, FSR, XeSS, ray tracing,
> frame generation, auto exposure, or a visual preset. Those features belong in
> independent provider mods built against Vulcanite.

## What 0.3 Alpha provides

Vulcanite `0.3.0-alpha.1` turns the project into a provider-facing prerequisite
with three stable, pure-Java SPIs:

- **Upscaler Provider** records one real-frame upscale into an MGF-owned output.
- **Frame Generation Provider** declares generated-frame requirements and may
  produce at most one generated frame per real frame when an adapter supports it.
- **PresentHook Provider** receives bounded callbacks before and after
  MGF-owned presentation without owning the swapchain or calling present.

Provider selection is deterministic and dependency-aware. Sessions receive
explicit resize, first-frame, resource-reload, world-change, device-replacement,
and shutdown lifecycle events. Recoverable and fatal failures preserve the real
Minecraft frame instead of leaving a partially modified output.

The stable `mgf-api` artifact contains no Fabric, Minecraft, Mojang, LWJGL, or
implementation types in its public signatures. Provider implementations receive
opaque native handles and immutable resource/state records. Vulcanite owns
barriers, image allocation, command-buffer insertion, output copy-back, resize,
reload, and device shutdown.

## Minecraft 26.2 adapter boundary

The current player JAR is specifically built for Minecraft 26.2. Its provider
adapter guarantees:

- the fully composed native-size SRGB main color image;
- one real presentation per Minecraft frame;
- MGF-owned Vulkan output images and explicit synchronization;
- same-frame vanilla fallback when a provider skips or fails;
- no provider GPU allocation or command recording when no provider is active.

Minecraft 26.2 does not expose a verified separate low-resolution scene image,
depth, motion vectors, camera parameters, reactive/transparency masks, HDR
input, or a safe multi-present path at this integration point. Providers that
require those resources remain registered but unsupported.

Frame Generation is therefore reported as
`UNSUPPORTED/multi_present_unsupported` on the 26.2 adapter. Vulcanite does not
fabricate temporal data or attempt an unsafe second present.

## Existing graphics foundation

Provider APIs build on the lower-level Vulcanite integration already available
to compatible mods:

- active OpenGL/Vulkan backend and capability reporting;
- Vulkan device-extension negotiation before logical-device creation;
- live instance, device, queue-family, queue, and VMA allocator handles;
- ordered Minecraft frame-graph callbacks and backend-aware fail-soft behavior;
- custom pipelines, generated/resource shaders, recursive includes, and reload
  warm-up;
- Vulkan compute programs, VMA-owned resources, explicit barriers, deterministic
  readback, and OpenGL unavailable reasons.

The earlier bundled auto-exposure and vignette effects were removed from the
player mod. Low-level compute remains available, but visual behavior must be
implemented by a separate dependent mod.

## Artifacts for players and developers

| Audience | Artifact | Purpose |
|---|---|---|
| Players and modpacks | `mgf-0.3.0-alpha.1+mc26.2.jar` | Installable client mod with embedded API |
| Provider developers | `dev.mgf:mgf-api:0.3.0-alpha.1` | Stable dependency-free contracts |
| Fabric development/runtime | `dev.mgf:mgf-fabric-26.2:0.3.0-alpha.1` | Minecraft 26.2 adapter |

Provider projects compile against `mgf-api` only and declare the
`mgf:providers` Fabric entrypoint:

```json
{
  "entrypoints": {
    "mgf:providers": ["com.example.ExampleProviderRegistrar"]
  },
  "depends": {
    "mgf": ">=0.3.0-alpha.1"
  }
}
```

The repository includes a compile-tested minimal provider and complete lifecycle,
resource, synchronization, migration, and publication documentation.

## Requirements and compatibility

| Item | Requirement |
|---|---|
| Minecraft | Exactly 26.2 for the current player JAR |
| Fabric Loader | 0.19.3 or newer |
| Java | 25 or newer |
| Fabric API | Not required by the player JAR |
| Vulkan providers | Minecraft Vulkan backend and provider-specific GPU/driver support |
| OpenGL | Supported fail-soft path for compatible APIs |

- **Sodium:** tested on Minecraft 26.2 with Vulkan and OpenGL.
- **VulkanMod and Sulkan:** incompatible because they replace the backend that
  Vulcanite integrates with.
- Each Minecraft rendering drop requires its own implementation adapter. The
  26.2 JAR is not a 26.3-compatible binary.

## Installation

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Place `mgf-0.3.0-alpha.1+mc26.2.jar` in the client `mods` directory.
3. Install the provider mod that declares Vulcanite as a dependency.
4. Select Minecraft's Vulkan graphics backend when that provider requires it.

Installing only Vulcanite is valid and leaves the original frame path unchanged.

## Validation status

The 0.3 Alpha release passed:

- full Gradle build, Javadoc/doclint, and API signature compatibility checks;
- Vulkan, OpenGL, Sodium/Vulkan, and Sodium/OpenGL client smoke tests;
- no-provider, no-op provider, Vulkan passthrough, recoverable failure, and fatal
  failure paths;
- resize, resource reload, world lifecycle, device shutdown, and provider close;
- Khronos Vulkan validation with no `VUID-`, `SYNC-HAZARD-`, `UNASSIGNED-`,
  `Validation Error`, or `Validation Warning` matches.

Runtime validation currently uses an NVIDIA GeForce RTX 4060 with driver
610.62. AMD and Intel Vulkan drivers remain unverified. This is still an Alpha
API, and Minecraft's Vulkan backend is experimental.

## Source and documentation

- Source: [github.com/PrliStrxs/Vulcanite](https://github.com/PrliStrxs/Vulcanite)
- Developer guide: `docs/README.md`
- Provider SPI: `docs/provider-spi.md`
- Minimal provider: `samples/sample-provider`
- Migration from 0.2: `docs/migration-0.2-to-0.3.md`

## License

- **`mgf-api`:** MIT. Provider mods may depend on and bundle the stable API under
  those terms.
- **Vulcanite player mod and 26.2 implementation:** PolyForm Shield 1.0.0.
  Modpack inclusion is welcome; repackaging Vulcanite into a competing product
  is not.

---

<details>
<summary>Chinese summary / 中文简介</summary>

Vulcanite 是面向 Minecraft 26.x 的客户端图形前置框架。0.3 Alpha 提供纯
Java 的 Upscaler、Frame Generation 和 PresentHook Provider API，并由
Minecraft 26.2 适配层统一管理 Vulkan 资源、同步、生命周期和失败回退。

只安装 Vulcanite 不会改变曝光、色彩、天空、暗角、分辨率或帧率，也不会
自动获得 DLSS、FSR、XeSS、光追或插帧。这些功能必须由依赖 Vulcanite 的
独立 Provider 模组实现。

当前 26.2 适配层只保证原生分辨率、SRGB、最终合成颜色，不提供独立低分辨率
场景颜色、深度、运动矢量、相机参数或安全的多次 Present。因此 Frame
Generation 会明确报告 `UNSUPPORTED/multi_present_unsupported`，不会尝试
不安全的第二次 Present。

玩家版本为 `mgf-0.3.0-alpha.1+mc26.2.jar`，需要 Minecraft 26.2、Fabric
Loader 0.19.3+ 和 Java 25+。玩家 JAR 不要求 Fabric API。Sodium 已在
Vulkan 和 OpenGL 下测试；VulkanMod、Sulkan 等替换渲染后端的模组不兼容。

0.3 Alpha 已通过完整构建、API 签名兼容、九组客户端运行矩阵和 Vulkan
Validation。当前硬件验证以 NVIDIA GeForce RTX 4060 为主，AMD/Intel
仍待补充。

</details>
