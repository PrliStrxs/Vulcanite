package dev.mgf.api.upscale;

/** Projection jitter sample in normalized render-resolution coordinates. */
public record JitterSequence(int index, int period, float offsetX, float offsetY) {

    public JitterSequence {
        if (index < 0 || period < 1 || index >= period
                || !Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("invalid jitter sequence sample");
        }
    }

    public static JitterSequence none() {
        return new JitterSequence(0, 1, 0.0F, 0.0F);
    }
}
