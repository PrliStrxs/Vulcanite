package dev.mgf.smoke;

import java.util.ArrayList;
import java.util.List;

import dev.mgf.api.CapsTier;
import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.Mgf;
import dev.mgf.api.MgfRuntime;

/**
 * The assertion set. Each check appends a human-readable line; failures make
 * the whole run FAIL. Checks branch on the backend the Gradle harness said to
 * expect ({@code -Dmgf.smoke.expectedBackend}).
 */
final class SmokeChecks {

    private final List<String> lines = new ArrayList<>();
    private boolean failed;

    private SmokeChecks() {
    }

    static SmokeChecks run(String expectedBackend) {
        SmokeChecks checks = new SmokeChecks();
        checks.lines.add("expectedBackend=" + expectedBackend);
        if (!Mgf.isAvailable()) {
            checks.fail("MGF runtime not available");
            return checks;
        }
        MgfRuntime runtime = Mgf.runtime();
        checks.lines.add("mgfVersion=" + runtime.version());

        if ("vulkan".equalsIgnoreCase(expectedBackend)) {
            checks.runVulkanChecks(runtime);
        } else {
            checks.runOpenGlChecks(runtime);
        }
        return checks;
    }

    private void runVulkanChecks(MgfRuntime runtime) {
        check(runtime.activeBackend() == GraphicsBackendKind.VULKAN,
                "activeBackend=" + runtime.activeBackend() + " (expected VULKAN)");
        check(runtime.caps().tier() == CapsTier.VULKAN_FULL,
                "tier=" + runtime.caps().tier() + " (expected VULKAN_FULL)");
        check(runtime.caps().extensionNegotiationActive(), "negotiationActive="
                + runtime.caps().extensionNegotiationActive());
        check(runtime.caps().hasDeviceExtension(SmokeVulkanBoot.EXT_AVAILABLE),
                "capsSeesRequestedExtension=" + runtime.caps().hasDeviceExtension(SmokeVulkanBoot.EXT_AVAILABLE));

        check(SmokeVulkanBoot.callbackFired, "onDeviceCreatedFired=" + SmokeVulkanBoot.callbackFired);
        if (SmokeVulkanBoot.callbackFired) {
            check(SmokeVulkanBoot.availableEnabled,
                    "callbackSawExtensionEnabled=" + SmokeVulkanBoot.availableEnabled);
            check(SmokeVulkanBoot.missingRequired != null
                            && SmokeVulkanBoot.missingRequired.contains(SmokeVulkanBoot.EXT_NONEXISTENT),
                    "missingRequired=" + SmokeVulkanBoot.missingRequired
                            + " (expected to contain " + SmokeVulkanBoot.EXT_NONEXISTENT + ")");
            check(SmokeVulkanBoot.callbackVkDevice != 0L,
                    "callbackVkDevice=0x" + Long.toHexString(SmokeVulkanBoot.callbackVkDevice));
        }

        runtime.vkInterop().ifPresentOrElse(interop -> {
            check(interop.vkInstance() != 0L, "vkInstance=0x" + Long.toHexString(interop.vkInstance()));
            check(interop.vkDevice() != 0L, "vkDevice=0x" + Long.toHexString(interop.vkDevice()));
            check(interop.graphicsQueue() != 0L, "graphicsQueue=0x" + Long.toHexString(interop.graphicsQueue()));
            check(interop.computeQueue() != 0L, "computeQueue=0x" + Long.toHexString(interop.computeQueue()));
            check(interop.transferQueue() != 0L, "transferQueue=0x" + Long.toHexString(interop.transferQueue()));
            check(interop.vmaAllocator() != 0L, "vma=0x" + Long.toHexString(interop.vmaAllocator()));
        }, () -> fail("vkInterop empty on Vulkan backend"));
    }

    private void runOpenGlChecks(MgfRuntime runtime) {
        check(runtime.activeBackend() == GraphicsBackendKind.OPENGL,
                "activeBackend=" + runtime.activeBackend() + " (expected OPENGL)");
        check(runtime.caps().tier() == CapsTier.OPENGL_COMPAT,
                "tier=" + runtime.caps().tier() + " (expected OPENGL_COMPAT)");
        check(runtime.vkInterop().isEmpty(), "vkInteropEmpty=" + runtime.vkInterop().isEmpty());
        check(runtime.caps().enabledDeviceExtensions().isEmpty(),
                "deviceExtensionsEmpty=" + runtime.caps().enabledDeviceExtensions().isEmpty());
        check(!SmokeVulkanBoot.callbackFired,
                "onDeviceCreatedNotFired=" + !SmokeVulkanBoot.callbackFired);
    }

    private void check(boolean ok, String detail) {
        lines.add((ok ? "OK   " : "FAIL ") + detail);
        if (!ok) {
            failed = true;
        }
    }

    private void fail(String detail) {
        check(false, detail);
    }

    boolean passed() {
        return !failed;
    }

    List<String> lines() {
        return lines;
    }
}
