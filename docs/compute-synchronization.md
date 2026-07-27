# Compute synchronization and lifecycle

This document defines the Vulkan ownership, resource-state, synchronization,
and lifecycle contract for MGF compute work. It applies to the generic M4
compute dispatcher and to the visible luminance-histogram auto-exposure sample.

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
| Histogram and exposure buffers | MGF compute effect | Device-scoped. Destroy before the owning VMA allocator. |
| Compute pipelines and layouts | MGF compute dispatcher/effect | Device-scoped. Close after queued uses complete. |
| Auto-exposure output storage image and view | MGF compute effect | Match the current main-target dimensions; retire old allocations through deferred destruction. |
| Frame-graph resource handles | Minecraft frame graph | Valid for one graph build/execution only. Replace `targets.main` with every `readsAndWrites` result. |

Public `ComputeDispatcher.Program` and `ComputeDispatcher.Buffer` objects are
owned by their caller. A standalone caller that dispatched work must call
`submitAndWait()` before host access or close; this blocking path must stay out
of frame-graph effects and other per-frame rendering code.

## Main color contract

Minecraft 26.2 creates the main color target as `RGBA8_UNORM` with usage bits
for transfer destination, transfer source, sampled texture, and render
attachment. It does not have `VK_IMAGE_USAGE_STORAGE_BIT`. Therefore:

- Histogram compute may sample the main color image.
- Copy-back may write the main color image as a transfer destination.
- Compute must not bind the main color image as a storage image.
- A visible compute effect writes a separate storage image, then copies that
  image back to main.

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

Auto exposure is an internal `BEFORE_EXECUTE` finalizer. MGF dispatches ordinary
frame-graph listeners before internal finalizers, and registers auto exposure
before the M2 PostFx finalizer. The intended order is:

1. Vanilla world passes.
2. Ordinary MGF/consumer frame-graph passes, including M3 world geometry.
3. Auto-exposure histogram, reduction, application, and copy-back.
4. M2 PostFx overlays.
5. Frame-graph execution returns to the rest of `GameRenderer`.

The auto-exposure pass declares `readsAndWrites(targets.main)` and stores the
returned handle back in `targets.main`. This makes the write visible to the
frame graph and forces later PostFx passes to consume the exposed image.

This hook affects world rendering. First-person hands, screen effects, entity
outlines, the selected vanilla post chain, and the GUI are rendered later.

## Resource states and barriers

Synchronization must be self-contained. Do not depend on an unrelated later
vanilla render pass to provide a broad memory barrier for the next frame.

| Transition | Source scope | Destination scope |
|---|---|---|
| Previous histogram reduction read -> histogram clear | compute shader / storage read | transfer / transfer write |
| Histogram clear -> histogram dispatch | transfer / transfer write | compute shader / storage read and write |
| Prior main rendering -> histogram sample | all commands / memory write | compute shader / sampled read |
| Histogram dispatch -> reduction | compute shader / storage write | compute shader / storage read |
| Previous exposure read -> current reduction write | compute or transfer / storage or transfer read | compute shader / storage read and write |
| Reduction -> exposure application | compute shader / storage write | compute shader / storage read |
| Previous output copy -> output storage write | transfer / transfer read | compute shader / storage write |
| Exposure application -> output copy | compute shader / storage write | transfer / transfer read |
| Main histogram sample -> main copy-back | compute shader / sampled read | transfer / transfer write |
| Main copy-back -> PostFx/render/transfer consumers | transfer / transfer write | color output, fragment shader, and transfer / attachment read-write, sampled read, and transfer read |

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
allocations may remain alive while disconnected, but temporal exposure state
must reset before the first frame of a new world. Exposure history must not leak
between servers or dimensions when the desired visual contract calls for a new
adaptation sequence.

### Shutdown and device replacement

Release MGF-owned Vulkan objects before Minecraft destroys the VMA allocator and
logical device. First make pending command buffers submit-visible, wait for the
graphics queue once, then destroy all effect and dispatcher objects. Cleanup
must be fail-soft: one failed object must not prevent vanilla device shutdown.
Never destroy borrowed main-target or sampler-cache objects.

## OpenGL fail-soft behavior

Registration is backend-independent, but execution is not. On OpenGL, the
compute service reports unavailable, emits one concise disabled reason, allocates
no Vulkan resources, and adds no auto-exposure write pass. Existing M2 PostFx and
M3 graphics pipelines continue through the OpenGL backend unchanged.

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
reporting. It does not enter a world, so visible auto exposure, frame-graph
ordering, resize, world reset, and shutdown still require an in-world run with
validation enabled before release.
