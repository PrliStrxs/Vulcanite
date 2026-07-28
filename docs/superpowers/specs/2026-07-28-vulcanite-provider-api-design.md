# Vulcanite 0.3 Provider API Design

**Status:** Approved

**Target:** `0.3.0-alpha.1`

**Project:** `H:\Project\Minecraft fabric\MGF`
**Date:** 2026-07-28

## 1. Goal

Vulcanite 0.3 turns the current rendering framework into a productized
prerequisite mod for downstream graphics mods. It publishes a dependency-free,
documented Java API and provides Minecraft 26.2 adapters for three provider
roles:

1. image upscaling;
2. frame generation;
3. presentation hooks.

Vulcanite does not ship an upscaler, frame generator, visual preset, vignette,
or auto-exposure effect. Installing only the `mgf` player JAR must preserve the
vanilla image and present path. With no active provider, 0.3 performs no GPU
allocation, command recording, copy, barrier, or extra presentation work.

DLSS, FSR, XeSS, NVOF, and comparable integrations remain separate mods. Those
mods compile against `mgf-api` and register providers through a Fabric
entrypoint; they do not compile against Minecraft, LWJGL, or `mgf-impl-26.2`
types unless they independently choose to add game-specific integration.

## 2. Scope

### 2.1 Included in 0.3

- A stable, pure-Java provider API in `mgf-api`.
- Deterministic provider discovery, support probing, selection, and diagnostics.
- Upscaler, Frame Generation, and PresentHook SPIs.
- Plain resource descriptors containing opaque numeric native handles,
  dimensions, native format values, state snapshots, ownership, and lifetime.
- Plain frame contexts containing frame identity, timing, dimensions, camera
  matrices, jitter, reset state, and optional temporal inputs.
- Minecraft 26.2/Vulkan adapters that translate final scene and presentation
  resources into the stable contracts.
- MGF-owned synchronization, borrowed-resource restoration, output resources,
  resize handling, reload handling, world transitions, device replacement,
  shutdown, fallback, and present sequencing.
- A Maven publication for `mgf-api`, source and Javadoc JARs, API signature
  checks, developer guides, a migration guide, and a compile-tested example.
- Performance telemetry and structural tests for the provider-free fast path.

### 2.2 Explicitly excluded

- A bundled DLSS, FSR, XeSS, NVOF, or other vendor implementation.
- A bundled visual effect or automatic visual configuration.
- A public generic Vulkan command-buffer or resource allocator API.
- Dedicated-compute queue scheduling for borrowed Minecraft images.
- Provider-controlled `vkQueuePresentKHR`; MGF remains the presentation owner.
- Guaranteed vanilla motion vectors. Minecraft 26.2 does not expose them, so
  motion vectors, reactive masks, transparency masks, optical flow, and
  exposure are optional inputs with explicit availability flags.
- API compatibility with an unverified Minecraft 26.3 implementation.

## 3. Design Alternatives

Three boundaries were considered:

1. Expose Minecraft and LWJGL objects directly. This is easy to implement but
   couples every consumer to one game drop and one Java binding version.
2. Expose a generic rendering abstraction owned by MGF. This is flexible but
   would turn 0.3 into a replacement RHI and duplicate Blaze3D.
3. Publish narrow pure-Java provider contracts and keep per-drop translation in
   `mgf-impl-26.2`.

Option 3 is selected. It matches the prerequisite-mod goal, protects consumer
binary compatibility, and keeps MGF responsible for the fragile game seams.

## 4. Module Boundary

### 4.1 `mgf-api`

`mgf-api` remains dependency-free. Public signatures may use only JDK types and
types defined by `mgf-api`. A build check rejects references to:

- `com.mojang.*`;
- `net.minecraft.*`;
- `org.lwjgl.*`;
- `net.fabricmc.*`;
- `dev.mgf.impl.*`.

The API contains contracts, validation, immutable value objects, and the static
runtime entrypoint. It contains no service discovery implementation, mixins,
native calls, allocation, logging backend, or Minecraft version checks.

### 4.2 `mgf-impl-26.2`

The implementation module owns:

- Fabric entrypoint discovery;
- Minecraft 26.2 mixins and adapters;
- resource capture and descriptor translation;
- provider arbitration and live session state;
- command-buffer acquisition and submission ordering;
- barriers and borrowed-resource state restoration;
- output image allocation and retirement;
- final blit, generated/real frame sequencing, and present;
- lifecycle events, fallback, diagnostics, and performance counters.

No implementation class appears in a stable API signature.

### 4.3 Samples and old effects

`ComputeEffects`, `ComputeAutoExposureRegistry`, `VulkanAutoExposure`, and the
auto-exposure sample are removed. The generic M4 compute dispatcher may remain
an explicitly unstable per-drop diagnostic API, but it is not part of the
provider contract and never runs unless a development sample calls it.

The vignette and custom geometry continue to live only in
`samples:sample-interop`; they are excluded from the player JAR.

## 5. Stable Package Layout

```text
dev.mgf.api
  Mgf
  MgfRuntime
  GraphicsBackendKind
  GraphicsCaps

dev.mgf.api.provider
  MgfProviderRegistrar
  ProviderRegistry
  ProviderDescriptor
  ProviderId
  ProviderKind
  ProviderEnvironment
  ProviderSupport
  ProviderResult
  ProviderResultCode
  ProviderSelection
  ProviderSelections
  ProviderSessionContext
  ResetReason
  FrameInfo
  FrameDimensions
  FrameMatrices
  CommandRecordingContext
  BorrowedImage
  ImageState
  ImageOwnership
  ImageLifetime
  ColorEncoding

dev.mgf.api.upscale
  UpscalerProvider
  UpscalerCapabilities
  UpscalerRequirements
  UpscalerSession
  UpscaleFrame
  UpscaleResources
  UpscaleParameters

dev.mgf.api.framegen
  FrameGenerationProvider
  FrameGenerationCapabilities
  FrameGenerationRequirements
  FrameGenerationSession
  FrameGenerationFrame
  FrameGenerationResources

dev.mgf.api.present
  PresentHookProvider
  PresentHookCapabilities
  PresentHookSession
  PresentBatch
  PresentFrame
  PresentFrameKind
  PresentReceipt
```

The package names are stable from 0.3 onward. Additions are allowed within a
minor version; removals and incompatible signature changes require a major
version once the project leaves Alpha.

## 6. Provider Discovery and Selection

### 6.1 Registration

Downstream mods register one entrypoint:

```json
{
  "entrypoints": {
    "mgf:providers": ["com.example.ExampleProviders"]
  }
}
```

```java
public final class ExampleProviders implements MgfProviderRegistrar {
    @Override
    public void registerProviders(ProviderRegistry registry) {
        registry.registerUpscaler(new ExampleUpscaler());
        registry.registerFrameGenerator(new ExampleFrameGenerator());
        registry.registerPresentHook(new ExamplePresentHook());
    }
}
```

`mgf-api` declares the registrar and registry interfaces. `mgf-impl-26.2` asks
Fabric Loader for entrypoints during client initialization, catches failures per
registrar, validates each descriptor, and freezes an immutable registration
snapshot before device selection.

Provider IDs use `namespace:path` lowercase syntax and are unique per role.
Duplicate IDs are rejected with one diagnostic; they never replace an earlier
registration. Provider descriptors include ID, display name, provider version,
priority, and the minimum MGF API major/minor version.

Registration must be side-effect-free: it must not query or create a graphics
device, allocate native resources, or depend on a world being loaded.

### 6.2 Probe and deterministic arbitration

MGF probes providers only after the live graphics device is known. The probe
environment reports the active backend, enabled extensions, opaque device and
queue handles, queue-family indices, device generation, and the frame inputs
the 26.2 adapter can supply.

Selection order is:

1. an explicitly configured provider ID, when it is registered and supported;
2. otherwise the supported provider with the highest integer priority;
3. ties are resolved by lexicographic provider ID.

MGF selects the upscaler first, the frame generator second, and the present hook
third. Frame Generation support is evaluated against the selected upscaler and
available temporal inputs. PresentHook support is evaluated against the final
real/generated frame plan. Only one provider per role is active.

The optional configuration keys are:

```properties
upscaler=auto
frame_generation=auto
present_hook=auto
```

`auto`, `off`, and an exact provider ID are accepted. Invalid or unsupported
values fail soft to `off` for that role and appear in diagnostics. 0.3 does not
add a settings screen.

`MgfRuntime.providers()` returns a read-only `ProviderSelections` snapshot with
registered IDs, selected IDs, support/failure reasons, and session state. This
is diagnostic state, not a mutation API.

## 7. Common Contracts

### 7.1 Opaque native data

Native dispatchable and non-dispatchable handles are `long`. Native image
formats, layouts, stage masks, access masks, and usage masks are numeric fields
whose meaning is documented for the active backend. Consumers may translate
them in their own JNI/LWJGL layer, but no native binding type crosses the API.

`ProviderEnvironment` and `BorrowedImage` are immutable snapshots. A zero handle
means unavailable and is never passed for a required resource. Every descriptor
also carries a device generation and resource generation so stale resources can
be rejected before a callback.

### 7.2 Ownership and lifetime

MGF owns all Minecraft images, output images, command buffers, barriers,
submissions, semaphores, swapchain acquisition, final blits, and present calls.
Providers borrow these objects only for the duration of the current callback.
They must not retain, destroy, resize, submit, present, or change ownership of a
borrowed object.

A command buffer supplied in `CommandRecordingContext` is already recording.
The provider records only its algorithm commands. It must not begin, end,
reset, or submit the command buffer and must not add barriers for MGF-managed
images. MGF inserts the advertised preconditions before the callback and
restores the postconditions before vanilla resumes.

Provider-private SDK objects and allocations are owned by that provider's live
session. They must be released in `close()` before the owning device is
destroyed. MGF catches close failures and continues device shutdown.

### 7.3 Threading

Registration is performed during Fabric client initialization. Probe, session
creation, frame callbacks, reset, resize, and close execute on the render thread.
Providers may use private worker threads internally, but they must join or stop
them in `close()` and may not call MGF frame objects from those threads.

### 7.4 Results and exceptions

Frame callbacks return `ProviderResult` with one of four codes:

- `SUCCESS`: output is valid;
- `SKIPPED`: expected no-output condition; use the real vanilla frame;
- `RECOVERABLE_FAILURE`: current output is invalid; fall back this frame;
- `FATAL_FAILURE`: disable the role for the current device session.

Every non-success result has a stable short code and a human-readable message.
Exceptions are treated as fatal failures and never escape into Minecraft.
Three consecutive recoverable failures disable that role for the current device
session. A success resets the counter. Failures cascade only to dependent roles:
an upscaler failure skips Frame Generation, while the real vanilla frame still
reaches PresentHook when that hook supports a real-only batch.

## 8. Upscaler SPI

`UpscalerProvider` exposes its descriptor and a probe method. A successful probe
returns capabilities and requirements, including supported scale range, color
encodings, quality modes, and required/optional depth, motion vectors, exposure,
reactive mask, and transparency mask.

`UpscalerSession` receives resize and reset notifications and records one
upscale operation per real frame. `UpscaleFrame` contains:

- frame ID, timing, reset/history state, and device/resource generations;
- render and display dimensions;
- current and previous view/projection matrices;
- jitter in render-pixel units;
- near/far plane and vertical field of view;
- borrowed input color and MGF-owned output color;
- optional depth, motion vectors, exposure, and masks;
- the MGF-owned recording command buffer.

The 26.2 adapter guarantees color. Depth is supplied when the live target has a
compatible depth view. Other temporal inputs are optional. A provider whose
required inputs are absent remains registered but is reported unsupported and
is not selected.

The provider writes only to the supplied output image. On success, MGF uses the
output-resolution image as the real presentation source. On skip or failure,
MGF uses the original Minecraft color image without copying it through an MGF
effect.

## 9. Frame Generation SPI

Frame Generation is probed after the upscaler selection. Capabilities declare
compatible upscaler IDs, required temporal inputs, supported color encodings,
and the maximum generated frames per real frame. 0.3 clamps the maximum to one.

`FrameGenerationSession` records work into an MGF command buffer and writes one
MGF-owned generated-frame image. Its context includes the current and previous
real presentation images, output dimensions, timing, matrices, optional depth,
motion vectors, optical flow, and UI mask, plus a reset/history flag.

MGF does not fabricate missing motion data. If a provider performs its own
motion or optical-flow analysis internally, it may declare those external
inputs optional and use its private resources. If it declares an input required,
selection remains disabled until that input exists.

A successful result creates the ordered batch `[GENERATED, REAL]`. A skip or
failure creates `[REAL]`. Real is always present and always last, which keeps
input sampling and game-state progression tied to real frames.

## 10. PresentHook SPI

PresentHook does not transfer swapchain ownership to a provider. It is a
controlled callback around each MGF-owned presentation and is suitable for
vendor pacing, latency markers, telemetry, and SDK-specific present metadata.

`PresentHookSession.beforePresent(PresentFrame)` is called after source-image
work and required barriers are recorded but before MGF performs the final blit,
submission, and `GpuSurface.present()`. `afterPresent(PresentReceipt)` reports
the frame ID, real/generated kind, present ordinal, result, and timing. A hook
must not invoke present itself.

The 26.2 adapter acquires a fresh surface image for every item in the batch and
honors the surface rule of one blit and one present per acquired image. If the
surface cannot safely execute a two-item batch, Frame Generation is marked
unsupported before session activation and the real-only vanilla path remains.

PresentHook failure disables only the hook. MGF immediately completes the same
frame through its normal present path.

## 11. Minecraft 26.2 Frame Flow

The active-provider path is:

1. Minecraft renders the world and later frame content into its main target.
2. The 26.2 adapter captures fresh color/depth views and dimensions; it never
   caches resize-sensitive Minecraft resources.
3. If an upscaler is active, MGF allocates or reuses an output image, inserts
   input/output barriers, and invokes the provider on the graphics queue.
4. MGF restores borrowed image state and chooses the provider output or the
   untouched Minecraft color as the real frame.
5. If Frame Generation is active and history is valid, MGF supplies current and
   previous resources, records generation, and forms a real-only or
   generated-plus-real batch.
6. PresentHook receives each planned presentation when active.
7. MGF performs final blit, encoder submission, pacing, and present for each
   accepted batch item.
8. MGF retires transient resources only after their in-flight use completes.

The provider-free path branches before step 2 and calls the original Minecraft
final blit, submit, and present code unchanged. It creates no frame descriptor
and allocates no output image.

The 26.2 Alpha adapter operates on the fully composed `mainRenderTarget` after
the GUI has rendered. It reports equal render and display dimensions and does
not alter Minecraft's internal world render resolution. Providers that require
a separate low-resolution world image, UI mask, motion vectors, or other inputs
remain registered but unsupported until a downstream integration supplies those
inputs or a later per-drop adapter adds them. MGF never substitutes fabricated
temporal data.

## 12. Synchronization and Lifecycle

All borrowed Minecraft images remain on the graphics queue. 0.3 does not use a
dedicated compute queue for scene or presentation resources. Queue-family
indices are ignored only when ownership does not change; otherwise MGF emits
the required transitions.

MGF defines and verifies an input state and output state per resource role. It
inserts provider pre-barriers, records provider commands in order, inserts
post-barriers, and restores the state expected by the final blit or subsequent
Minecraft work. Providers cannot override these states.

Lifecycle order is:

1. discover and validate registrations;
2. create device and probe support;
3. select roles and create sessions in upscaler/framegen/present order;
4. reset temporal history on first frame, world change, dimension change,
   resource reload, camera discontinuity, or provider change;
5. recreate size-dependent MGF outputs on resize and notify sessions;
6. close sessions in reverse order on device replacement or shutdown;
7. destroy MGF outputs only after in-flight completion and before VMA/device
   destruction.

World changes invalidate previous-frame images and temporal matrices. Resource
reload preserves provider registration but resets sessions. Resize increments
the resource generation so stale descriptors fail validation. A failed new
session never destroys the previous vanilla presentation path.

## 13. Neutrality and Fast Path

Core neutrality is a release gate:

- no provider means no MGF GPU commands or allocations;
- no core class registers auto exposure, vignette, tone mapping, sharpening,
  color grading, or dynamic resolution;
- the final source image and presentation count match vanilla;
- provider discovery and diagnostics may perform bounded CPU work at startup;
- no per-frame log line is emitted in normal operation.

The provider registry freezes into role-specific arrays. Frame code reads one
volatile active-session snapshot and does not scan registrations. Reusable
adapter state and output allocations are device-scoped. The framework CPU
target is below 0.10 ms average per active frame excluding provider work; the
provider-free adapter target is below 0.05 ms and zero per-frame heap allocation
after warmup. Runtime timings are diagnostics, not flaky CI pass/fail timers.

## 14. API Stability and Publication

The release version is `0.3.0-alpha.1`. Publications are:

```text
dev.mgf:mgf-api:0.3.0-alpha.1
dev.mgf:mgf-fabric-26.2:0.3.0-alpha.1
```

The player JAR name remains `mgf-0.3.0-alpha.1+mc26.2.jar` and embeds the API.
Both publications include source and Javadoc JARs. Gradle provides a local
staging repository under `build/repository`; remote credentials and publishing
are not required to build or test.

Javadoc runs with doclint enabled. Public and protected API signatures are
captured in a checked baseline and compared in `apiCompatibilityCheck`.
`check` also runs `jdeps`/constant-pool validation for forbidden dependencies,
API reflection tests, provider contract tests, and publication metadata tests.

The breaking removal or relocation of `dev.mgf.api.unstable.*` is allowed in
this Alpha. The migration guide maps every removed 0.2 entrypoint to its 0.3
replacement or states that it is intentionally unavailable.

## 15. Documentation Set

Tracked English Markdown documentation lives directly in this repository:

```text
docs/README.md
docs/getting-started.md
docs/provider-spi.md
docs/resource-lifecycle.md
docs/publishing.md
docs/migration-0.2-to-0.3.md
docs/examples/minimal-provider.md
```

The root README links to this set and clearly distinguishes the player JAR from
the API dependency. Code snippets are compiled from a real example source where
possible so documentation cannot silently drift from the API.

Local Word design sources remain ignored as `docs/*.docx`; Markdown product
documentation is version controlled.

## 16. Verification

### 16.1 Unit and contract tests

- descriptor and ID validation;
- duplicate registration and registrar failure isolation;
- deterministic selection, explicit `off`, explicit ID, priority, and tie
  behavior;
- dependency-aware upscaler/framegen/present selection;
- missing required resource rejection;
- session lifecycle order and render-thread enforcement;
- success, skip, recoverable, fatal, exception, three-strike, and dependent
  fallback behavior;
- resource generation and stale-handle rejection;
- provider-free zero-work counters;
- forbidden API dependency scan;
- Javadoc, sources, POM, and API compatibility tasks;
- compile-tested minimal downstream provider.

### 16.2 Runtime matrix

- Vulkan without providers: unchanged output route, one real present, zero MGF
  GPU work;
- OpenGL without providers: unchanged output route and no Vulkan allocation;
- Vulkan with a diagnostic no-op provider: registration, probe, session,
  resize, reload, world transition, shutdown, and fallback callbacks;
- Vulkan plus Sodium with and without the diagnostic provider;
- OpenGL with registered Vulkan-only providers: exact unsupported reasons and
  unchanged vanilla output;
- Vulkan validation with resize, reload, world enter/leave, provider failure,
  and shutdown;
- generated/real present ordering where the 26.2 surface reports multi-present
  support.

The diagnostic provider writes no visible effect. It records markers/counters
only and is packaged in a development sample, never in the player JAR.

### 16.3 Release evidence

The release report records exact Gradle commands, test results, runtime matrix,
artifact paths, file sizes, SHA-256 hashes, JAR metadata, Javadoc/source JARs,
staged Maven coordinates, forbidden-package scan, and remaining GPU/backend
coverage limits.

## 17. Acceptance Criteria

0.3 is complete only when:

- all three stable SPIs are present in `mgf-api` and documented;
- a downstream example compiles against `mgf-api` only;
- the 26.2 adapter invokes each role through real frame/present lifecycle seams;
- absent/unsupported/failing providers preserve the same-frame vanilla output;
- the core auto-exposure implementation and registration are gone;
- the player JAR contains no sample classes or sample assets;
- the no-provider Vulkan/OpenGL paths show zero MGF GPU work and no visual
  modification;
- lifecycle and synchronization validation passes for supported runtime paths;
- build, unit tests, Javadocs, publications, compatibility checks, and smoke
  matrix pass;
- developer, migration, lifecycle, and publishing documentation is tracked in
  the repository.

## 18. Known Risks

- Minecraft 26.2 has no stable public present or upscaling extension point; the
  final blit/present adapter remains version-fragile and must fail soft.
- Temporal algorithms cannot be selected when their required motion resources
  are absent. The API exposes this honestly rather than supplying fake data.
- Generated-frame presentation depends on safe repeated surface acquisition.
  Unsupported surface behavior disables Frame Generation without affecting
  the real frame.
- Raw native handles are powerful. Ownership, callback lifetime, and MGF-owned
  synchronization rules require strict validation and clear Javadocs.
- Automated GPU coverage may remain NVIDIA-heavy. Release notes must state the
  tested hardware and avoid claiming untested AMD or Intel runtime behavior.
