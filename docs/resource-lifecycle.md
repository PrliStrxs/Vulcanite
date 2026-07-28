# Resource and Lifecycle Contract

Vulcanite owns synchronization, image allocation, resize retirement, command
submission, and presentation. Providers record only within the supplied frame
callback.

## Handle Rules

`BorrowedImage` and `CommandRecordingContext` expose opaque native handles.
They do not transfer ownership and must not be closed, freed, submitted, or
retained beyond their declared lifetime.

- `ImageOwnership.MINECRAFT` means the image is borrowed from the current live
  Minecraft target.
- `ImageOwnership.MGF` means Vulcanite owns the output allocation; providers may
  write only as permitted by the callback contract.
- `ImageLifetime.CALLBACK` handles expire when the callback returns.
- `ImageLifetime.DEVICE_SESSION` handles remain valid only until reset, resize,
  device replacement, or session close as indicated by the supplied generation.

Validate `deviceGeneration` and `resourceGeneration` before reusing private
descriptors. Never cache a Minecraft image or view across frames or resize.

## Command Recording and Synchronization

The command buffer is already recording on Vulcanite's graphics queue. Providers
may encode their SDK or Vulkan work into it, but must not end it, submit it,
change queue ownership, or wait the graphics queue independently. Vulcanite
inserts pre/post barriers, restores borrowed image state, and performs final
copy/blit and submission.

Providers must respect `ImageState`, native format, native usage mask, dimensions,
and `ColorEncoding`. They may not replace layouts, access masks, stage masks, or
queue-family ownership outside the states supplied for the current callback.

## Resize and Reset

Size-dependent outputs are recreated before `resize(FrameDimensions)` is sent.
Temporal history is invalid after these reset reasons:

```text
FIRST_FRAME
RESIZE
RESOURCE_RELOAD
WORLD_CHANGE
DIMENSION_CHANGE
CAMERA_DISCONTINUITY
PROVIDER_CHANGE
DEVICE_REPLACED
```

Treat every reset as a requirement to discard temporal state before the next
frame. World and dimension changes invalidate previous images and matrices.
Resource reload preserves registration but resets active sessions.

## Shutdown and Threads

Vulcanite closes PresentHook, Frame Generation, then Upscaler before destroying
device-owned output images and the Vulkan allocator/device. `close()` must be
idempotent. A provider may use private worker threads, but it must not pass MGF
frame objects to them and must stop or join them before `close()` returns.

Provider code must not call Minecraft rendering APIs from callbacks or worker
threads. All SPI lifecycle methods are render-thread calls; use private SDK
synchronization only when it cannot outlive the callback/session contract.

## Neutral Fast Path

When no role is active, the adapter branches before descriptors, allocations,
or command recording and invokes Minecraft's original final presentation path.
Unsupported, skipped, failed, resized, or reloaded providers must always preserve
a valid same-frame real output.
