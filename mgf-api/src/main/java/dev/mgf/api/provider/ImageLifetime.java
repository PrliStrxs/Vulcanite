package dev.mgf.api.provider;

/** Maximum lifetime for which a provider may use an image descriptor. */
public enum ImageLifetime {
    /** The descriptor and its handles expire when the current callback returns. */
    CALLBACK,
    /** The descriptor remains valid until the owning provider session closes. */
    DEVICE_SESSION
}
