package dev.mgf.impl.mixin;

import java.util.Collection;
import java.util.Set;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.vk.VulkanBootNegotiation;

/**
 * The extension-negotiation seam (design doc §7, seam #1 — HIGH fragility).
 *
 * <p>Vanilla builds its device extension set as a local {@code HashSet} and its
 * feature set as a constant, then calls the private static
 * {@code createDevice(Collection, VulkanPhysicalDevice, Set)}. There is no
 * public seam, so we rewrite that call's arguments. Verified against 26.2:
 * {@code javap} shows exactly one such invocation inside the public
 * {@code createDevice(long, ShaderSource, GpuDebugOptions, Runnable)}.
 *
 * <p>Fail-soft: {@code defaultRequire: 0} — if the target moves in a future
 * drop, the game boots without negotiation and {@link dev.mgf.impl.core.SeamHealth}
 * reports the seam as not engaged.
 */
@Mixin(VulkanBackend.class)
public abstract class VulkanBackendMixin {

    @ModifyArgs(
            method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)Lcom/mojang/blaze3d/systems/GpuDevice;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"
            )
    )
    private void mgf$negotiateDeviceCreation(Args args) {
        try {
            Collection<String> extensions = args.get(0);
            VulkanPhysicalDevice physicalDevice = args.get(1);
            Set<VulkanFeature> features = args.get(2);

            // Mutates the collections in place: the same HashSet flows into the
            // VulkanDevice constructor, keeping DeviceInfo.underlyingExtensions
            // accurate (see VulkanBootNegotiation.negotiate javadoc).
            VulkanBootNegotiation.negotiate(extensions, features, physicalDevice);
        } catch (Throwable t) {
            // Fail-soft: never let negotiation break device creation — a boot
            // crash would trip vanilla's watchdog and force the player to OpenGL.
            MgfConstants.LOGGER.error("Vulkan boot negotiation failed; continuing with vanilla arguments", t);
        }
    }
}
