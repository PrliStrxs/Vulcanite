package dev.mgf.api.framegen;

import java.util.Objects;
import java.util.Set;

import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.ProviderId;

/** Frame-generation compatibility with the exactly-one 1.0 output contract. */
public record FrameGenerationCapabilities(
        Set<ColorEncoding> colorEncodings,
        Set<ProviderId> compatibleUpscalers,
        int maximumGeneratedFrames,
        FrameGenerationMode mode) {

    public FrameGenerationCapabilities {
        colorEncodings = Set.copyOf(colorEncodings);
        compatibleUpscalers = Set.copyOf(compatibleUpscalers);
        mode = Objects.requireNonNull(mode, "mode");
        if (colorEncodings.isEmpty()) {
            throw new IllegalArgumentException("colorEncodings must not be empty");
        }
        if (maximumGeneratedFrames != 1) {
            throw new IllegalArgumentException("1.0 supports exactly one generated frame");
        }
    }

    public FrameGenerationCapabilities(
            Set<ColorEncoding> colorEncodings,
            Set<ProviderId> compatibleUpscalers,
            int maximumGeneratedFrames) {
        this(colorEncodings, compatibleUpscalers, maximumGeneratedFrames, FrameGenerationMode.STANDARD);
    }

    public boolean acceptsUpscaler(ProviderId upscaler) {
        return compatibleUpscalers.isEmpty() || compatibleUpscalers.contains(upscaler);
    }

    public boolean nvidiaExperimental() {
        return mode == FrameGenerationMode.NVIDIA_EXPERIMENTAL;
    }
}
