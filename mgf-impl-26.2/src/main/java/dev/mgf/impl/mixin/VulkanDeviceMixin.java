package dev.mgf.impl.mixin;

import com.mojang.blaze3d.vulkan.VulkanDevice;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.compute.ComputeServiceRegistry;
import dev.mgf.impl.provider.ProviderFrameBridge;
import dev.mgf.impl.vk.VulkanBootNegotiation;

/**
 * The device-created seam (design doc §6.3): fires consumer
 * {@code onDeviceCreated} callbacks at the tail of the {@code VulkanDevice}
 * constructor — queues, VMA, and the command encoder are live by then
 * (bytecode-verified on 26.2: they are all assigned before the constructor
 * returns).
 *
 * <p>Fail-soft like every MGF mixin: {@code defaultRequire: 0}, and the
 * dispatch itself never throws upward.
 */
@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mgf$afterDeviceCreated(CallbackInfo ci) {
        try {
            VulkanBootNegotiation.fireDeviceCreated((VulkanDevice) (Object) this);
            ProviderFrameBridge.onDeviceCreated((VulkanDevice) (Object) this);
        } catch (Throwable t) {
            MgfConstants.LOGGER.error("Device-created dispatch failed; continuing", t);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mgf$beforeDeviceClose(CallbackInfo ci) {
        try {
            ProviderFrameBridge.onDeviceClosing((VulkanDevice) (Object) this);
        } catch (Throwable t) {
            MgfConstants.LOGGER.error("Provider shutdown failed; continuing Vulkan device destruction", t);
        }
        try {
            ComputeServiceRegistry.onDeviceClosing((VulkanDevice) (Object) this);
        } catch (Throwable t) {
            MgfConstants.LOGGER.error("Compute shutdown failed; continuing Vulkan device destruction", t);
        }
    }
}
