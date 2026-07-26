package dev.mgf.api.graph;

/**
 * Points in vanilla's per-frame graph assembly where MGF can invoke listeners.
 *
 * <p>Anchor availability is version- and neighbor-dependent: mods that replace
 * parts of the level renderer (e.g. Sodium) may prevent per-pass anchors from
 * firing. {@link #BEFORE_EXECUTE} is the designed always-safe anchor; gate on
 * {@link dev.mgf.api.GraphicsCaps#frameGraphEventsActive()} and treat the
 * others as best-effort.
 */
public enum FrameGraphAnchor {
    /** After vanilla queued the sky pass. */
    AFTER_SKY,
    /** After vanilla queued the main terrain/entity pass. */
    AFTER_MAIN,
    /** After vanilla queued the weather pass. */
    AFTER_WEATHER,
    /** Immediately before the frame graph executes — the last chance to add passes. */
    BEFORE_EXECUTE
}
