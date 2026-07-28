package dev.mgf.api.upscale;

import java.util.HashSet;
import java.util.Set;

import dev.mgf.api.provider.FrameResourceKind;

/** Required and optional frame inputs for an upscaler. */
public record UpscalerRequirements(
        Set<FrameResourceKind> requiredResources,
        Set<FrameResourceKind> optionalResources) {

    public UpscalerRequirements {
        requiredResources = Set.copyOf(requiredResources);
        optionalResources = Set.copyOf(optionalResources);
        Set<FrameResourceKind> overlap = new HashSet<>(requiredResources);
        overlap.retainAll(optionalResources);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("resources cannot be both required and optional: " + overlap);
        }
        if (!requiredResources.contains(FrameResourceKind.COLOR)) {
            throw new IllegalArgumentException("upscaler must require COLOR");
        }
    }
}
