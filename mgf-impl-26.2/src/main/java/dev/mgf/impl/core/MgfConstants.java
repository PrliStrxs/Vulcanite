package dev.mgf.impl.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants for the MGF implementation. */
public final class MgfConstants {

    public static final String MOD_ID = "mgf";
    public static final String ENTRYPOINT_VULKAN_BOOT = "mgf:vulkan_boot";
    public static final String ENTRYPOINT_PROVIDERS = "mgf:providers";
    public static final Logger LOGGER = LoggerFactory.getLogger("MGF");

    private MgfConstants() {
    }
}
