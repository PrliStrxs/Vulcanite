package dev.mgf.impl.upscale;

import dev.mgf.api.upscale.JitterSequence;

/** Deterministic Halton jitter sequence for future low-resolution projections. */
public final class ProjectionJitterController {

    private final int period;
    private int index;

    public ProjectionJitterController(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be positive");
        }
        this.period = period;
    }

    public JitterSequence next() {
        int current = index;
        index = (index + 1) % period;
        return new JitterSequence(
                current, period,
                halton(current + 1, 2) - 0.5F,
                halton(current + 1, 3) - 0.5F);
    }

    public void reset() {
        index = 0;
    }

    private static float halton(int value, int base) {
        float fraction = 1.0F;
        float result = 0.0F;
        int current = value;
        while (current > 0) {
            fraction /= base;
            result += fraction * (current % base);
            current /= base;
        }
        return result;
    }
}
