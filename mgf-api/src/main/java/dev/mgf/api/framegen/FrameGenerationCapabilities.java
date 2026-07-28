package dev.mgf.api.framegen;

import java.util.Set;

import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.ProviderId;

/** Frame-generation compatibility with the exactly-one 0.3 output contract. */
public record FrameGenerationCapabilities(
        Set<ColorEncoding> colorEncodings,
        Set<ProviderId> compatibleUpscalers,
        int maximumGeneratedFrames) {

    public FrameGenerationCapabilities {
        colorEncodings = Set.copyOf(colorEncodings);
        compatibleUpscalers = Set.copyOf(compatibleUpscalers);
        if (colorEncodings.isEmpty()) {
            throw new IllegalArgumentException("colorEncodings must not be empty");
        }
        if (maximumGeneratedFrames != 1) {
            throw new IllegalArgumentException("0.3 supports exactly one generated frame");
        }
    }

    public boolean acceptsUpscaler(ProviderId upscaler) {
        return compatibleUpscalers.isEmpty() || compatibleUpscalers.contains(upscaler);
    }
}
