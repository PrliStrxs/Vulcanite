package dev.mgf.api.provider;

/** Frame inputs a provider may require or consume. */
public enum FrameResourceKind {
    COLOR,
    DEPTH,
    MOTION_VECTORS,
    EXPOSURE,
    REACTIVE_MASK,
    TRANSPARENCY_MASK,
    OPTICAL_FLOW,
    UI_MASK,
    MATRICES
}
