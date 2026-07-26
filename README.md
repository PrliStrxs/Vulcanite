# MGF — Minecraft Graphics Framework

A Fabric-prerequisite rendering framework for the Minecraft 26.x Vulkan era.

MGF is an **augmentation layer over vanilla's Blaze3D abstraction** (`GpuDevice` /
`FrameGraphBuilder` / dual GL+Vulkan backends), not a replacement RHI. It adds the
seams vanilla lacks — Vulkan extension negotiation, native interop, frame-graph
events, compute, presentation hooks — behind a semver-stable API that insulates
consumer mods from per-drop churn. See the design document
(`docs/MGF_技术设计文档_V2.0.docx`, kept out of version control) for the full
architecture, roadmap, and rationale.

## Modules

| Project | Artifact | What it is |
|---|---|---|
| `:mgf-api` | `mgf-api` | Stable consumer API. Pure Java — no Minecraft, LWJGL, or loader types. |
| `:mgf-impl-26.2` | `mgf` | The mod players install. All mixins, access wideners, and 26.2-specific adapters. Bundles `mgf-api`. |
| `:samples:sample-interop` | dev-only | Requests Vulkan extensions via MGF and logs negotiated device state at startup (M0/M1 verification). |
| `smoke-test/` | — | Launch-and-assert harness; wired into the build in M1 (see its README). |

Consumer mods depend on `mgf-api` only. Each Minecraft drop gets its own
`mgf-impl-<drop>` project; the API stays put.

## Building

```
./gradlew build
```

Requires JDK 25 (Gradle toolchains will fetch one if missing). No mappings are
involved — 26.x is unobfuscated and code targets Mojang's real names.

## Running the M0 spike

```
./gradlew :samples:sample-interop:runClient
```

Then in the generated run directory set `preferredGraphicsBackend:"vulkan"` in
`options.txt` (or switch Graphics API in video settings) and check the log for:

- `MGF seam engaged: EXTENSION_NEGOTIATION`
- `Mod 'mgf-sample-interop': enabling Vulkan device extension VK_NV_optical_flow`
- `Requested extension ... -> enabled=true`
- `VkInterop: instance=0x... device=0x...` with non-zero handles

Acceptance criteria and the fail-soft policy are defined in design doc §10 (M0)
and §7. Test **both** backends every session — vanilla's auto-fallback can
switch backends silently.

## Code layout rules

- One concern per file; no god classes. Mixins live in `dev.mgf.impl.mixin`,
  one target class per mixin.
- `mgf-api` public signatures never reference `com.mojang.blaze3d` types.
- Every fragile vanilla seam reports to `SeamHealth` and degrades instead of
  crashing — nothing may throw during device creation (vanilla's watchdog
  would force players back to OpenGL).
