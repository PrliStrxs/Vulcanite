package dev.mgf.api.upscale;

/** How UI is represented relative to the upscaler input and output. */
public enum UiCompositionHint {
    UNKNOWN,
    UI_ALREADY_IN_INPUT,
    UI_COMPOSED_AFTER_UPSCALE,
    UI_MASK_RESOURCE
}
