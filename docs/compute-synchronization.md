# Compute synchronization and lifecycle

This document defines the Vulkan ownership, resource-state, synchronization,
and lifecycle contract for MGF compute work. It applies to the generic M4
compute dispatcher and the 0.3 provider adapter. Vulcanite 0.3 does not register
or execute a bundled compute effect.

## Non-negotiable rules

- Record compute work on the render thread.
- Execute image-dependent compute work on vanilla's graphics queue.
- Insert raw command buffers with `VulkanCommandEncoder.execute(...)` and let
  vanilla submit the frame.
- Keep vanilla images in `VK_IMAGE_LAYOUT_GENERAL`.
- Treat every Minecraft-owned image, image view, sampler, queue, command
  encoder, and VMA allocator as borrowed.
- Never call `submitAndWait()` from a per-frame effect. It is for explicit CPU
  readback and diagnostics only.
- Do not add Vulkan work or allocate Vulkan resources on OpenGL.

## Ownership

| Resource | Owner | MGF contract |
|---|---|---|
| Main color texture and image | Minecraft `MainTarget` | Borrow for the current pass only. Never cache across resize and never destroy. |
| Main color texture view | Minecraft `MainTarget` | Borrow for the current pass only. Push a fresh descriptor after every resize. |
| Clamp-to-edge sampler | `RenderSystem.getSamplerCache()` | Borrow and never close. |
| Graphics queue and command encoder | Minecraft `VulkanDevice` | Record through the live encoder. Do not submit a parallel frame timeline. |
| Generic compute buffers, pipelines, and layouts | MGF compute dispatcher caller | Device-scoped. Close after queued uses complete and before the owning VMA allocator. |
| Provider outputs, snapshots, and history | MGF provider adapter | Size-dependent `RGBA8_UNORM` storage/sampled/transfer images; expose callback-scoped descriptors and retire resized generations through deferred destruction. |
| Frame-graph resource handles | Minecraft frame graph | Valid for one graph build/execution only. Replace `targets.main` with every `readsAndWrites` result. |

Public `ComputeDispatcher.Program` and `ComputeDispatcher.Buffer` objects are
owned by their caller. A standalone caller that dispatched work must call
`submitAndWait()` before host access or close; this blocking path must stay out
of frame-graph effects and other per-frame rendering code.

## Main color contract

Minecraft 26.2 creates the main color target as `RGBA8_UNORM` with usage bits
for transfer destination, transfer source, sampled texture, and render
attachment. It does not have `VK_IMAGE_USAGE_STORAGE_BIT`. Therefore:

- Provider compute may read the main color image through the advertised sampled
  or transfer state.
- Successful provider output may be copied to the main image as a transfer
  destination.
- Compute must not bind the main color image as a storage image.
- Algorithms that require storage writes use an MGF-owned output image.

The Vulkan backend initializes textures in `VK_IMAGE_LAYOUT_GENERAL` and uses
that layout for render attachments, sampled descriptors, clears, and copies.
MGF barriers must use `GENERAL -> GENERAL` for the main image. Transitioning it
to `SHADER_READ_ONLY_OPTIMAL` or a transfer-only layout would violate vanilla's
state assumptions unless MGF restored `GENERAL` before returning control.

Resolve the native handles inside the frame-pass task, not while registering
the listener and not during mod initialization:

```java
RenderTarget target = mainHandle.get();
VulkanGpuTexture texture =
        (VulkanGpuTexture) Objects.requireNonNull(target.getColorTexture());
VulkanGpuTextureView view =
        (VulkanGpuTextureView) Objects.requireNonNull(target.getColorTextureView());
VulkanGpuSampler sampler = (VulkanGpuSampler) RenderSystem.getSamplerCache()
        .getClampToEdge(FilterMode.NEAREST);
```

`texture.vkImage()`, `view.vkImageView()`, and `sampler.vkSampler()` are borrowed
handles. A resize can replace the first two before the next frame.

## Frame-graph ordering

Vulcanite 0.3 registers no core frame-graph effect. Development samples may
register explicit listeners, but they are not packaged in the player JAR. The
Provider adapter runs later at the final composed main-target blit/present seam,
after GUI rendering, and does not claim access to a separate world-only image.

## Resource states and barriers

Synchronization must be self-contained. Do not depend on an unrelated later
vanilla render pass to provide a broad memory barrier for the next frame.

| Transition | Source scope | Destination scope |
|---|---|---|
| Prior main rendering -> provider read | all commands / memory write | provider-declared shader or transfer read |
| New MGF output -> provider write | top of pipe / none | provider-declared shader write |
| Provider output write -> copy source | provider-declared shader write | transfer / transfer read |
| Main provider read -> copy destination | provider-declared read | transfer / transfer write |
| Provider output copy -> final blit | transfer / transfer write | transfer / transfer read |
| Real snapshot copy -> history/provider read | transfer / transfer write | provider-declared shader or transfer read |

Image barriers cover color aspect, mip level zero, and array layer zero. Queue
family indices remain `VK_QUEUE_FAMILY_IGNORED` because all work stays on the
graphics queue. Buffer barriers cover the exact owned allocation range.

When generic compute copies a GPU result into a vanilla uniform buffer, add
both sides of the transfer explicitly:

1. Previous fragment uniform read -> transfer write.
2. Compute storage write -> transfer read.
3. Copy the result into a `USAGE_UNIFORM | USAGE_COPY_DST` buffer.
4. Transfer write -> fragment uniform read.

Vanilla uniform buffers are not storage buffers. Do not bind one as an SSBO.

## Provider final-composite contract

The Minecraft 26.2 provider adapter runs after the fully composed main target,
including GUI rendering. The adapter reports equal render and display sizes and
guarantees only the final color image. It does not claim depth, motion vectors,
a separate low-resolution scene, or a UI mask. Providers that require those
inputs remain registered but unsupported.

Minecraft's main image has no storage-image usage. Provider algorithms write
only to MGF-owned images created with storage, sampled, transfer-source, and
transfer-destination usage. The 26.2 Upscaler path records this copy after a
successful callback:

1. Provider output to Minecraft main.

Frame Generation remains `UNSUPPORTED/multi_present_unsupported` in this
adapter. Snapshot, generated-output, and real-restore copies belong to a later
adapter that can prove a safe multi-present contract.

All provider images remain in `VK_IMAGE_LAYOUT_GENERAL` after their first
initialization barrier. MGF records provider preconditions and postconditions,
owns every command buffer and barrier, and restores the Minecraft main image to
transfer-read access before `GpuSurface.blitFromTexture(...)`. Providers must
not begin, end, reset, submit, or add barriers for an MGF command buffer.

The adapter allocates transient command buffers through the live
`VulkanCommandEncoder`, ends them, and enqueues them with `execute(...)`.
It never calls `submit()` from the per-frame provider bridge. Minecraft 26.2
performs exactly one real present; no second acquire or present is attempted.

## Graphics-queue policy

The main image uses exclusive sharing and belongs to the graphics queue family.
MGF allocates a transient command buffer from vanilla's graphics command pool,
records compute into it, ends it, and calls `VulkanCommandEncoder.execute(...)`.
This preserves command order inside vanilla's pending graphics submission. The
normal frame-end submit then carries world rendering, compute, PostFx, and
presentation synchronization together.

Do not use the exposed dedicated compute queue for main-color effects. That
route requires queue-family ownership transfers for the main image, release and
acquire barriers on both queues, and semaphore or timeline coordination with
vanilla's graphics submission. Dedicated-queue scheduling is intentionally
deferred until MGF owns a complete cross-queue frame-graph contract.

## Lifecycle

### Device creation

Create fixed buffers and compute programs lazily on the render thread for the
live `VulkanDevice`. Keep ownership keyed to that device identity. Do not make a
capability query allocate resources on an arbitrary thread.

### Resource reload

Embedded compute shader sources and their device objects may survive a resource
reload because they are independent of vanilla's shader cache. M3 render
pipelines registered for warm-up are recompiled after vanilla replaces that
cache. If compute shaders later become resource-pack-backed, compile replacement
objects first and swap only after successful compilation; retain the old objects
on failure.

### Resize

Read main width, height, texture, and view again every frame. Recompute dispatch
group counts from the current dimensions. Recreate the owned output image when
its dimensions differ, and queue the previous image/view for delayed destruction
instead of destroying resources that an in-flight frame may still reference.

### World transition

The level frame graph does not run on title screens. Device-scoped programs and
allocations may remain alive while disconnected, but active provider temporal
state resets before the first frame of a new world. Previous-frame images and
matrices must not leak between servers or dimensions.

### Shutdown and device replacement

Release MGF-owned Vulkan objects before Minecraft destroys the VMA allocator and
logical device. First make pending command buffers submit-visible, wait for the
graphics queue once, then destroy all effect and dispatcher objects. Cleanup
must be fail-soft: one failed object must not prevent vanilla device shutdown.
Never destroy borrowed main-target or sampler-cache objects.

## OpenGL fail-soft behavior

Registration is backend-independent, but execution is not. On OpenGL, the
compute service reports unavailable, emits one concise disabled reason, allocates
no Vulkan resources, and opens no Provider session. Existing graphics pipelines
continue through the OpenGL backend unchanged.

An unsupported backend is an expected capability result, not an exception and
not a reason to fail client startup.

## Validation

Run the normal Vulkan and OpenGL smoke tests, then run Vulkan synchronization
validation:

```text
./gradlew :smoke-test:smokeTest
./gradlew :smoke-test:smokeTest -PsmokeBackend=opengl
./gradlew :smoke-test:smokeTest -PsmokeValidation
```

`-PsmokeValidation` adds `--vulkanValidation`, verifies that Khronos validation
actually enabled, and fails on `VUID-`, `SYNC-HAZARD-`, `UNASSIGNED-`,
`Validation Error`, or `Validation Warning` lines in `latest.log`.

The automated smoke run verifies generic compute dispatch/readback, repeated
dispatch barriers, survival across a resource reload, and OpenGL fail-soft
reporting. It does not enter a world, so in-world resources, interactive resize,
world transition resets, and a real downstream Provider require a separate run
with validation enabled.
