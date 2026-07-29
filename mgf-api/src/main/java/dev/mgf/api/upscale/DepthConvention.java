package dev.mgf.api.upscale;

/** Depth interpretation for a verified depth input resource. */
public record DepthConvention(boolean reversed, float minDepth, float maxDepth) {

    public DepthConvention {
        if (!Float.isFinite(minDepth) || !Float.isFinite(maxDepth)
                || minDepth < 0.0F || maxDepth > 1.0F || minDepth >= maxDepth) {
            throw new IllegalArgumentException("depth range must be finite and within [0, 1]");
        }
    }

    public static DepthConvention zeroToOne() {
        return new DepthConvention(false, 0.0F, 1.0F);
    }

    public static DepthConvention reversedZeroToOne() {
        return new DepthConvention(true, 0.0F, 1.0F);
    }
}
