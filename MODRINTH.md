# Vulcanite

**A neutral graphics-provider prerequisite for Minecraft 26.x.**

Vulcanite (`mgf`) is a client-side Fabric library mod. It provides stable
provider APIs and a Minecraft 26.2 rendering adapter so independent graphics
mods can build temporal upscaling, NVIDIA super-resolution, DLSS-style
integrations, frame-pacing hooks, and future rendering experiments without each
mod directly hooking Minecraft internals.

> Installing Vulcanite alone does not change exposure, color, sky, vignette,
> render scale, UI, gameplay, or frame rate. It does not bundle DLSS, FSR, XeSS,
> NVIDIA Streamline, ray tracing, Frame Generation SDKs, native binaries, or a
> visual preset. Those features belong in separate provider mods.

## What 1.0 provides

- Stable pure-Java `mgf-api` contracts for Upscaler, Frame Generation capability
  probing, and PresentHook providers.
- Temporal upscaling resource contracts for color, output, depth, motion
  vectors, jitter, matrices, exposure, reactive mask, transparency mask, and UI
  mask.
- Explicit depth convention, motion-vector convention, jitter sequence, exposure
  mode, UI composition hint, render-scale, quality-mode, and reset metadata.
- Opaque Vulkan handles through `BorrowedImage` and `CommandRecordingContext`
  without Minecraft, Fabric, Mojang, LWJGL, or implementation public types.
- Stable unsupported reason codes for missing resources, backend mismatch,
  unsupported render scale, provider failures, and unsafe multi-present.
- NVIDIA-first experimental Frame Generation capability lane, gated by explicit
  config and adapter checks. It is not a stable 1.0 activation guarantee.
- No-provider neutrality: no provider image allocation, no provider command
  recording, no extra present, and no visual changes.

## Minecraft 26.2 adapter boundary

The 26.2 adapter verifies native SRGB color and SDR identity exposure metadata.
It reports unverified temporal inputs with deterministic reason codes such as
`depth_unavailable`, `motion_vectors_unavailable`, `matrices_unavailable`,
`ui_composition_unavailable`, and `multi_present_unsupported`.

This means provider authors can build against the 1.0 API now, including DLSS 2
or NVIDIA super-resolution provider mods, while Vulcanite remains honest about
which runtime inputs are actually available on the installed adapter. Vulcanite
does not fabricate depth, motion vectors, low-resolution scene color, matrices,
or safe multi-present.

## Requirements

| Item | Requirement |
|---|---|
| Minecraft | Exactly 26.2 for this player JAR |
| Fabric Loader | 0.19.3 or newer |
| Java | 25 or newer |
| Fabric API | Not required by the player JAR |
| Vulkan providers | Minecraft Vulkan backend and provider-specific GPU/driver support |
| OpenGL | Fail-soft diagnostics for Vulkan-only providers |

Sodium is tested on the supported matrix. VulkanMod and Sulkan are incompatible
because they replace the backend Vulcanite integrates with.

## Developer entry points

Provider mods compile against:

```text
dev.mgf:mgf-api:1.0.0
```

and register:

```json
{
  "entrypoints": {
    "mgf:providers": ["com.example.ExampleProviderRegistrar"]
  },
  "depends": {
    "mgf": ">=1.0.0"
  }
}
```

Documentation is included in the source repository:

- `docs/getting-started.md`
- `docs/provider-spi.md`
- `docs/temporal-upscaling.md`
- `docs/provider-conformance.md`
- `docs/resource-lifecycle.md`
- `docs/migration-0.3-to-1.0.md`

## Validation status

The release is verified with full Gradle build, API signature check, Javadocs,
local Maven staging publication, sample-provider compilation, smoke tests, and
jar content scanning. Vulkan runtime validation is NVIDIA-first; AMD and Intel
remain documented as unverified for this release.

<details>
<summary>Chinese summary / 中文简介</summary>

Vulcanite 是 Minecraft 26.x 的客户端画面/渲染前置框架。1.0 的重点是稳定
Provider API：后续 DLSS 2、NVIDIA 超分、FSR、XeSS、AI 超分、插帧实验等
独立模组可以依赖 `mgf-api` 开发，不需要各自直接 hook Minecraft 内部。

只安装 Vulcanite 不会改变画面、玩法、曝光、颜色、UI、分辨率或帧率，也不会
内置 DLSS、FSR、XeSS、光追、插帧 SDK 或 native binary。当前 26.2 适配层
只会暴露已经验证的资源；没有验证的深度、运动向量、矩阵、UI composition、
multi-present 等会用稳定 reason code 返回 `UNSUPPORTED`。

</details>
