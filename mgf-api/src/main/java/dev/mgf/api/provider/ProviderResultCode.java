package dev.mgf.api.provider;

/** Outcome of one provider frame callback. */
public enum ProviderResultCode {
    SUCCESS,
    SKIPPED,
    RECOVERABLE_FAILURE,
    FATAL_FAILURE
}
