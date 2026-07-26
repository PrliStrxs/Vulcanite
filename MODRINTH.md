# Vulcanite

**The rendering framework for Minecraft's Vulkan era.**

Minecraft 26.2 ships an experimental Vulkan backend — but exposes none of it to mods. No way to request device extensions. No access to the native device. No hooks into device creation. Any mod that wants modern GPU features has to patch game internals by hand and re-patch them every update.

Vulcanite is a client-side **prerequisite (library) mod** that opens those doors once, properly, behind a stable API — so the mods built on top of it can do things that used to be impossible on Java Minecraft.

---

## What it does today (0.1 alpha)

- 🔌 **Vulkan extension negotiation** — declare the device extensions your mod needs (e.g. `VK_NV_optical_flow`, `VK_KHR_external_memory`) and Vulcanite enables them at device creation, with per-GPU availability checks and clean per-mod reporting when something isn't supported.
- 🤝 **Native interop** — raw `VkInstance` / `VkDevice` / physical device / graphics, compute and transfer queues / VMA allocator handles, delivered through a callback the moment the device exists. Hand them straight to your native bridge.
- 🧭 **Capability tiers** — `VULKAN_FULL` / `VULKAN_BASIC` / `OPENGL_COMPAT` runtime detection, so dependent mods can gate features and degrade gracefully instead of crashing.
- 🛡️ **Fail-soft by design** — every game-version-specific hook verifies itself at runtime. If a future drop breaks a seam, the game still boots and features disable themselves with a clear log line. Vulcanite can never brick your launch into the OpenGL fallback.

## What this unlocks

Vulcanite is the foundation layer. Features like these live in mods **built on** Vulcanite — and with the plumbing already done, they become focused integration projects instead of months of renderer surgery:

- **Real upscaling** (DLSS-class, FSR-class) on the native Vulkan backend
- **Real ray tracing** via `VK_KHR_ray_query`-capable hardware
- **AI frame interpolation / frame generation** using vendor optical-flow hardware
- **Custom render passes and post-processing chains** (frame-graph event API — in development)

Works on any Vulkan 1.2+ GPU in principle; currently developed and tested on NVIDIA GeForce RTX. Vendor-specific features (optical flow, DLSS) additionally depend on your hardware.

## For developers

Add the entrypoint, declare what you need, receive live handles:

```json
"entrypoints": {
  "mgf:vulkan_boot": [ "com.example.MyVulkanBoot" ]
},
"depends": { "mgf": "*" }
```

```java
public final class MyVulkanBoot implements VulkanBootRegistrar {
    @Override
    public void configureVulkan(VulkanBootConfigurator c) {
        c.requestDeviceExtension("VK_NV_optical_flow", true);
        c.onDeviceCreated(result -> {
            if (result.missingRequiredExtensions().isEmpty()) {
                long device = result.interop().vkDevice();
                long computeQueue = result.interop().computeQueue();
                // hand off to your native bridge
            }
        });
    }
}
```

**The stability promise:** your mod compiles against `mgf-api` only — no `com.mojang.blaze3d` types in public signatures. Each Minecraft drop breaks Vulcanite once, internally; it does not break every mod built on it.

Mod id / dependency id: `mgf`.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 (later drops supported as they release) |
| Loader | Fabric ≥ 0.19.3, Java 25 |
| Full features | Vulkan 1.2+ GPU & driver, `Graphics API: Vulkan` |
| OpenGL backend | Loads harmlessly, Vulkan features report unavailable |

**Compatibility:** Sodium ✅ (tested). Backend-replacing mods (VulkanMod, Sulkan) ❌ — architecturally incompatible with any Blaze3D-level framework.

## Status & roadmap

⚠️ **Experimental alpha.** The vanilla Vulkan backend itself is experimental; expect rough edges. Every release is gated on an automated launch-and-assert test suite on both backends.

Roadmap: frame-graph pass injection → pipeline/shader helpers → compute dispatch → upscaler & frame-generation provider slots. Follow the project for updates.

---

<details>
<summary>中文简介 (Chinese)</summary>

**Vulcanite — Minecraft Vulkan 时代的渲染前置框架。**

Minecraft 26.2 引入了实验性 Vulkan 后端，但没有给模组暴露任何接口：无法申请设备扩展、拿不到原生设备句柄、没有设备创建钩子。Vulcanite 作为客户端前置模组，把这些能力以稳定 API 的形式开放出来。

**当前版本（0.1 alpha）提供：**

- **Vulkan 扩展协商** — 声明所需设备扩展（如 `VK_NV_optical_flow`），Vulcanite 在设备创建时启用，按 GPU 支持情况过滤并逐模组上报缺失；
- **原生互操作** — 设备创建完成即通过回调交付 `VkInstance` / `VkDevice` / 图形、计算、传输队列 / VMA 分配器等原始句柄，可直接传给原生桥接层；
- **能力分层** — `VULKAN_FULL` / `VULKAN_BASIC` / `OPENGL_COMPAT` 运行时检测，下游模组按层降级而不是崩溃；
- **Fail-soft 设计** — 所有版本相关钩子运行时自检，未来版本变动只会让对应功能自动停用并留日志，绝不会导致游戏无法启动。

**它为下游解锁的方向**（作为基于 Vulcanite 的模组实现）：原生 Vulkan 后端上的真·超分（DLSS/FSR 级）、真·光线追踪（`VK_KHR_ray_query`）、AI 插帧/帧生成（厂商光流硬件）、自定义渲染 Pass 与后处理链（帧图事件 API 开发中）。

理论上支持任何 Vulkan 1.2+ 显卡；目前在 NVIDIA GeForce RTX 上开发和测试。依赖 id：`mgf`。兼容 Sodium（已实测）；与替换后端类模组（VulkanMod、Sulkan）互斥。

⚠️ 实验性 alpha；原版 Vulkan 后端本身也是实验性的。每个版本发布前都会通过双后端自动化启动断言测试。

</details>
