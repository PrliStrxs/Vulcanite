package dev.mgf.impl.provider;

import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.framegen.FrameGenerationFrame;
import dev.mgf.api.framegen.FrameGenerationResources;
import dev.mgf.api.present.PresentFrame;
import dev.mgf.api.present.PresentFrameKind;
import dev.mgf.api.present.PresentReceipt;
import dev.mgf.api.provider.BorrowedImage;
import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.FrameInfo;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ImageLifetime;
import dev.mgf.api.provider.ImageOwnership;
import dev.mgf.api.provider.ImageState;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderResultCode;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.upscale.UpscaleParameters;
import dev.mgf.api.upscale.UpscaleResources;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.vk.VkInteropImpl;

/** Final-composite provider adapter for Minecraft 26.2's blit/present path. */
public final class ProviderFrameBridge {

    private static final int MAIN_USAGE = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            | VK_IMAGE_USAGE_SAMPLED_BIT
            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

    private static VulkanDevice device;
    private static VulkanProviderResources<VulkanProviderImage> resources;
    private static PendingFrame pending;
    private static long deviceGeneration;
    private static long nextFrameId;
    private static long previousFrameNanos;

    private ProviderFrameBridge() {
    }

    public static void onDeviceCreated(VulkanDevice createdDevice) {
        Objects.requireNonNull(createdDevice, "createdDevice");
        if (device == createdDevice) {
            return;
        }
        device = createdDevice;
        deviceGeneration = Math.addExact(deviceGeneration, 1);
        ProviderEnvironment environment = new ProviderEnvironment(
                GraphicsBackendKind.VULKAN,
                deviceGeneration,
                Optional.of(new VkInteropImpl(createdDevice)),
                Set.of(FrameResourceKind.COLOR),
                true);
        ProviderRuntime.current().open(environment);
        if (deviceGeneration > 1) {
            ProviderRuntime.current().requestReset(ResetReason.DEVICE_REPLACED);
        }
    }

    public static void onDeviceClosing(VulkanDevice closingDevice) {
        if (device != closingDevice) {
            return;
        }
        ProviderRuntime runtime = ProviderRuntime.current();
        if (runtime.hasActiveProviders()) {
            closeStep("submit pending provider work", () -> closingDevice.createCommandEncoder().submit());
            closeStep("wait for provider work", () -> closingDevice.graphicsQueue().waitIdle());
        }
        closeStep("close provider sessions", runtime::close);
        VulkanProviderResources<VulkanProviderImage> closingResources = resources;
        resources = null;
        if (closingResources != null) {
            closeStep("destroy provider resources", closingResources::close);
        }
        pending = null;
        device = null;
        previousFrameNanos = 0L;
    }

    public static GpuTextureView beforeBlit(GpuTextureView original) {
        ProviderRuntime runtime = ProviderRuntime.current();
        return beforeBlit(original, runtime.hasActiveProviders(), ProviderFrameBridge::prepareActiveFrame);
    }

    static <T> T beforeBlit(T original, boolean active, Function<T, T> activePath) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(activePath, "activePath");
        if (!active) {
            return original;
        }
        return activePath.apply(original);
    }

    public static void present(GpuSurface surface) {
        Objects.requireNonNull(surface, "surface");
        PendingFrame current = pending;
        pending = null;
        if (current == null) {
            surface.present();
            return;
        }
        current.state().present(new SurfacePresenter(surface, current));
    }

    private static GpuTextureView prepareActiveFrame(GpuTextureView original) {
        try {
            if (!(original instanceof VulkanGpuTextureView view)
                    || !(view.texture() instanceof VulkanGpuTexture texture)
                    || device == null
                    || texture.getFormat() != GpuFormat.RGBA8_UNORM) {
                pending = null;
                return original;
            }

            ProviderRuntime runtime = ProviderRuntime.current();
            FrameDimensions dimensions = new FrameDimensions(
                    view.getWidth(0), view.getHeight(0), view.getWidth(0), view.getHeight(0));
            boolean frameProviderActive = runtime.upscalerActive() || runtime.frameGenerationActive();
            VulkanProviderResources<VulkanProviderImage> frameResources = null;
            boolean historyReset = false;
            long resourceGeneration = 1L;
            if (frameProviderActive) {
                frameResources = ensureResources();
                boolean resized = frameResources.ensure(dimensions);
                if (resized) {
                    runtime.resize(dimensions);
                }
                resourceGeneration = frameResources.resourceGeneration();
            }

            Optional<ResetReason> reset = runtime.applyPendingReset();
            if (frameResources != null && reset.isPresent()) {
                frameResources.reset(reset.orElseThrow());
            }
            historyReset = reset.isPresent()
                    || frameResources != null && !frameResources.historyValid();

            FrameInfo frameInfo = nextFrameInfo(historyReset, resourceGeneration);
            ProviderResult frameGenerationResult = ProviderResult.skipped(
                    "frame_generation_inactive", "No Frame Generation session is active");
            if (runtime.upscalerActive() || runtime.frameGenerationActive()) {
                frameGenerationResult = recordFrameProviders(
                        runtime, texture, view, dimensions, frameInfo, frameResources);
            }

            ProviderPresentState presentState = ProviderPresentState.fromFrameGeneration(frameGenerationResult);
            PresentFrameKind firstKind = presentState.generated()
                    ? PresentFrameKind.GENERATED : PresentFrameKind.REAL;
            invokeBeforePresent(runtime, frameInfo, firstKind, 0, texture, view);
            pending = new PendingFrame(presentState, view, texture, frameInfo);
            return original;
        } catch (Throwable throwable) {
            pending = null;
            MgfConstants.LOGGER.error("Provider frame preparation failed; using vanilla presentation", throwable);
            return original;
        }
    }

    private static ProviderResult recordFrameProviders(
            ProviderRuntime runtime,
            VulkanGpuTexture mainTexture,
            VulkanGpuTextureView mainView,
            FrameDimensions dimensions,
            FrameInfo frameInfo,
            VulkanProviderResources<VulkanProviderImage> frameResources) {
        ProviderResult upscalerResult = ProviderResult.skipped(
                "upscaler_inactive", "No upscaler session is active");
        ProviderResult frameGenerationResult = ProviderResult.skipped(
                "frame_generation_inactive", "No Frame Generation session is active");
        int width = dimensions.displayWidth();
        int height = dimensions.displayHeight();

        try (VulkanProviderCommandRecorder recorder = new VulkanProviderCommandRecorder(
                device, frameInfo.deviceGeneration(), frameInfo.resourceGeneration())) {
            if (runtime.upscalerActive()) {
                recorder.prepareProviderRead(mainTexture.vkImage());
                recorder.prepareProviderWrite(frameResources.upscaledOutput());
                BorrowedImage input = minecraftDescriptor(
                        mainTexture,
                        mainView,
                        recorder.providerReadState(),
                        frameInfo.resourceGeneration());
                BorrowedImage output = frameResources.upscaledOutput().descriptor(
                        ColorEncoding.SRGB,
                        recorder.providerReadWriteState(),
                        frameInfo.deviceGeneration(),
                        frameInfo.resourceGeneration());
                UpscaleFrame upscaleFrame = new UpscaleFrame(
                        frameInfo,
                        dimensions,
                        recorder.context(),
                        new UpscaleResources(input, output,
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty()),
                        new UpscaleParameters(
                                Optional.empty(), 0.0f, 0.0f, 0.05f, 1024.0f, 70.0f,
                                runtime.upscalerQualityMode().orElse("native")));
                upscalerResult = runtime.invokeUpscaler(session -> session.record(upscaleFrame));
                if (upscalerResult.code() == ProviderResultCode.SUCCESS) {
                    recorder.finishProviderWrite(frameResources.upscaledOutput());
                }
            }

            boolean upscaled = upscalerResult.code() == ProviderResultCode.SUCCESS;
            if (runtime.frameGenerationActive()) {
                if (upscaled) {
                    recorder.copyOwnedToOwned(
                            frameResources.upscaledOutput(), frameResources.realSnapshot(), width, height);
                } else {
                    recorder.copyMinecraftToOwned(
                            mainTexture.vkImage(), frameResources.realSnapshot(), width, height);
                }

                if (frameResources.historyValid()) {
                    recorder.prepareProviderRead(frameResources.realSnapshot().imageHandle());
                    recorder.prepareProviderRead(frameResources.previousReal().imageHandle());
                    recorder.prepareProviderWrite(frameResources.generatedOutput());
                    FrameGenerationFrame generatedFrame = new FrameGenerationFrame(
                            frameInfo,
                            dimensions,
                            recorder.context(),
                            new FrameGenerationResources(
                                    frameResources.realSnapshot().descriptor(
                                            ColorEncoding.SRGB,
                                            recorder.providerReadState(),
                                            frameInfo.deviceGeneration(),
                                            frameInfo.resourceGeneration()),
                                    Optional.of(frameResources.previousReal().descriptor(
                                            ColorEncoding.SRGB,
                                            recorder.providerReadState(),
                                            frameInfo.deviceGeneration(),
                                            frameInfo.resourceGeneration())),
                                    frameResources.generatedOutput().descriptor(
                                            ColorEncoding.SRGB,
                                            recorder.providerReadWriteState(),
                                            frameInfo.deviceGeneration(),
                                            frameInfo.resourceGeneration()),
                                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                            Optional.empty());
                    frameGenerationResult = runtime.invokeFrameGenerator(
                            session -> session.record(generatedFrame));
                    if (frameGenerationResult.code() == ProviderResultCode.SUCCESS) {
                        recorder.finishProviderWrite(frameResources.generatedOutput());
                    }
                } else {
                    frameGenerationResult = ProviderResult.skipped(
                            "history_unavailable", "Frame Generation history is not ready");
                }

                boolean copiedToMinecraft = copyOutputIfSuccessful(frameGenerationResult,
                        () -> recorder.copyOwnedToMinecraft(
                                frameResources.generatedOutput(), mainTexture.vkImage(), width, height));
                if (!copiedToMinecraft) {
                    copiedToMinecraft = copyOutputIfSuccessful(upscalerResult,
                            () -> recorder.copyOwnedToMinecraft(
                                    frameResources.realSnapshot(), mainTexture.vkImage(), width, height));
                }
                if (!copiedToMinecraft) {
                    recorder.prepareMinecraftForBlit(mainTexture.vkImage());
                }
                recorder.copyOwnedToOwned(
                        frameResources.realSnapshot(), frameResources.previousReal(), width, height);
            } else {
                boolean copiedToMinecraft = copyOutputIfSuccessful(upscalerResult,
                        () -> recorder.copyOwnedToMinecraft(
                                frameResources.upscaledOutput(), mainTexture.vkImage(), width, height));
                if (!copiedToMinecraft) {
                    recorder.prepareMinecraftForBlit(mainTexture.vkImage());
                }
            }
            recorder.finish();
            frameResources.markHistoryValid();
        }
        return frameGenerationResult;
    }

    static boolean copyOutputIfSuccessful(ProviderResult result, Runnable copyOutput) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(copyOutput, "copyOutput");
        if (result.code() != ProviderResultCode.SUCCESS) {
            return false;
        }
        copyOutput.run();
        return true;
    }

    private static void invokeBeforePresent(
            ProviderRuntime runtime,
            FrameInfo frameInfo,
            PresentFrameKind kind,
            int ordinal,
            VulkanGpuTexture texture,
            VulkanGpuTextureView view) {
        if (!runtime.presentHookActive()) {
            return;
        }
        try (VulkanProviderCommandRecorder recorder = new VulkanProviderCommandRecorder(
                device, frameInfo.deviceGeneration(), frameInfo.resourceGeneration())) {
            recorder.prepareProviderRead(texture.vkImage());
            BorrowedImage source = minecraftDescriptor(
                    texture, view, recorder.providerReadState(), frameInfo.resourceGeneration());
            PresentFrame frame = new PresentFrame(
                    frameInfo, kind, ordinal, source, Optional.of(recorder.context()));
            ProviderResult result = runtime.invokePresentHook(session -> session.beforePresent(frame));
            if (result.code() == ProviderResultCode.SUCCESS) {
                recorder.prepareMinecraftForBlit(texture.vkImage());
                recorder.finish();
            }
        }
    }

    private static BorrowedImage minecraftDescriptor(
            VulkanGpuTexture texture,
            VulkanGpuTextureView view,
            ImageState state,
            long resourceGeneration) {
        return new BorrowedImage(
                texture.vkImage(),
                Objects.requireNonNull(view, "view").vkImageView(),
                texture.getWidth(0),
                texture.getHeight(0),
                VK_FORMAT_R8G8B8A8_UNORM,
                Integer.toUnsignedLong(MAIN_USAGE),
                ColorEncoding.SRGB,
                state,
                ImageOwnership.MINECRAFT,
                ImageLifetime.CALLBACK,
                deviceGeneration,
                resourceGeneration);
    }

    private static FrameInfo nextFrameInfo(boolean historyReset, long resourceGeneration) {
        long now = System.nanoTime();
        double deltaSeconds = previousFrameNanos == 0L
                ? 0.0 : Math.max(0L, now - previousFrameNanos) / 1_000_000_000.0;
        previousFrameNanos = now;
        return new FrameInfo(nextFrameId++, deltaSeconds, historyReset, deviceGeneration, resourceGeneration);
    }

    private static VulkanProviderResources<VulkanProviderImage> ensureResources() {
        if (resources == null) {
            resources = VulkanProviderResources.create(device, deviceGeneration);
        }
        return resources;
    }

    private static void closeStep(String action, Runnable step) {
        try {
            step.run();
        } catch (Throwable throwable) {
            MgfConstants.LOGGER.error("Failed to {}; continuing provider shutdown", action, throwable);
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record PendingFrame(
            ProviderPresentState state,
            VulkanGpuTextureView view,
            VulkanGpuTexture mainTexture,
            FrameInfo frameInfo) {
    }

    private static final class SurfacePresenter implements ProviderPresentState.Presenter {
        private final GpuSurface surface;
        private final PendingFrame frame;

        private SurfacePresenter(GpuSurface surface, PendingFrame frame) {
            this.surface = surface;
            this.frame = frame;
        }

        @Override
        public void presentCurrent(PresentFrameKind kind) {
            int ordinal = frame.state().generated() && kind == PresentFrameKind.REAL ? 1 : 0;
            long started = System.nanoTime();
            boolean presented = false;
            String message = "";
            try {
                surface.present();
                presented = true;
            } catch (Throwable throwable) {
                message = describe(throwable);
                throw throwable;
            } finally {
                long duration = Math.max(0L, System.nanoTime() - started);
                boolean completed = presented;
                String receiptMessage = message;
                ProviderRuntime.current().afterPresent(session -> session.afterPresent(
                        new PresentReceipt(frame.frameInfo(), kind, ordinal,
                                completed, duration, receiptMessage)));
            }
        }

        @Override
        public void acquireReal() throws Exception {
            surface.acquireNextTexture();
        }

        @Override
        public void restoreReal() {
            VulkanProviderResources<VulkanProviderImage> frameResources = Objects.requireNonNull(resources);
            try (VulkanProviderCommandRecorder recorder = new VulkanProviderCommandRecorder(
                    device, frame.frameInfo().deviceGeneration(), frame.frameInfo().resourceGeneration())) {
                recorder.copyOwnedToMinecraft(
                        frameResources.realSnapshot(),
                        frame.mainTexture().vkImage(),
                        frame.view().getWidth(0),
                        frame.view().getHeight(0));
                recorder.finish();
            }
        }

        @Override
        public void blitAndSubmitReal() {
            invokeBeforePresent(
                    ProviderRuntime.current(), frame.frameInfo(), PresentFrameKind.REAL, 1,
                    frame.mainTexture(), frame.view());
            surface.blitFromTexture(RenderSystem.getDevice().createCommandEncoder(), frame.view());
            RenderSystem.getDevice().createCommandEncoder().submit();
        }

        @Override
        public void disableFrameGeneration(Throwable throwable) {
            ProviderRuntime.current().disableFrameGeneration("surface_acquire_failed", throwable);
        }
    }

}
