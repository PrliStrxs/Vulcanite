package dev.mgf.impl.provider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import dev.mgf.api.framegen.FrameGenerationSession;
import dev.mgf.api.present.PresentHookSession;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderKind;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderSelection;
import dev.mgf.api.provider.ProviderSelections;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.provider.ProviderSessionState;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.api.upscale.UpscalerSession;
import dev.mgf.impl.core.MgfConstants;

/** Owns selected provider sessions and their fail-soft device lifecycle. */
public final class ProviderRuntime {

    private static volatile ProviderRuntime global;

    private final ProviderCatalog catalog;
    private final ProviderConfig config;
    private final BooleanSupplier renderThread;
    private volatile RuntimeState state;
    private volatile ProviderSelections selections;
    private boolean upscalerSucceededThisFrame = true;

    public ProviderRuntime(
            ProviderCatalog catalog, ProviderConfig config, BooleanSupplier renderThread) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.config = Objects.requireNonNull(config, "config");
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        if (!catalog.isFrozen()) {
            throw new IllegalArgumentException("provider catalog must be frozen");
        }
        this.state = RuntimeState.inactive();
        this.selections = awaitingDeviceSelections(catalog, config);
    }

    public static void install(ProviderRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (global != null) {
            throw new IllegalStateException("provider runtime installed twice");
        }
        global = runtime;
    }

    public static ProviderRuntime current() {
        ProviderRuntime runtime = global;
        if (runtime == null) {
            throw new IllegalStateException("provider runtime is not installed");
        }
        return runtime;
    }

    public ProviderSelections selections() {
        return selections;
    }

    public void open(ProviderEnvironment environment) {
        requireRenderThread();
        Objects.requireNonNull(environment, "environment");
        if (state.active()) {
            closeSessions();
        }

        ProviderSessionContext context = new ProviderSessionContext(environment);
        ProviderSelector.SelectedUpscaler selectedUpscaler = ProviderSelector.selectUpscaler(
                catalog, config.upscaler(), environment);
        OpenedUpscaler upscaler = openUpscaler(selectedUpscaler, context);

        Optional<ProviderId> upscalerId = upscaler.session().isPresent()
                ? selectedUpscaler.diagnostic().selected() : Optional.empty();
        ProviderSelector.SelectedFrameGenerator selectedFrameGenerator = ProviderSelector.selectFrameGenerator(
                catalog, config.frameGeneration(), environment, upscalerId);
        OpenedFrameGenerator frameGenerator = openFrameGenerator(selectedFrameGenerator, context);

        Optional<ProviderId> frameGeneratorId = frameGenerator.session().isPresent()
                ? selectedFrameGenerator.diagnostic().selected() : Optional.empty();
        ProviderSelector.SelectedPresentHook selectedPresentHook = ProviderSelector.selectPresentHook(
                catalog, config.presentHook(), environment, frameGeneratorId);
        OpenedPresentHook presentHook = openPresentHook(selectedPresentHook, context);

        state = new RuntimeState(
                upscaler.session(), frameGenerator.session(), presentHook.session(),
                upscaler.failures(), frameGenerator.failures(), presentHook.failures(),
                upscaler.session().isPresent() && frameGenerator.session().isPresent());
        selections = new ProviderSelections(
                upscaler.diagnostic(), frameGenerator.diagnostic(), presentHook.diagnostic());
        upscalerSucceededThisFrame = true;
        logSelections("opened", selections);
    }

    public void resize(FrameDimensions dimensions) {
        requireRenderThread();
        Objects.requireNonNull(dimensions, "dimensions");
        RuntimeState current = state;
        invokeVoid(ProviderKind.UPSCALER, current.upscaler(), current.upscalerFailures(),
                session -> session.resize(dimensions));
        if (!current.frameGeneratorDependsOnUpscaler() || !current.upscalerFailures().disabled()) {
            invokeVoid(ProviderKind.FRAME_GENERATION, current.frameGenerator(), current.frameGeneratorFailures(),
                    session -> session.resize(dimensions));
        }
    }

    public void reset(ResetReason reason) {
        requireRenderThread();
        Objects.requireNonNull(reason, "reason");
        RuntimeState current = state;
        invokeVoid(ProviderKind.UPSCALER, current.upscaler(), current.upscalerFailures(),
                session -> session.reset(reason));
        if (!current.frameGeneratorDependsOnUpscaler() || !current.upscalerFailures().disabled()) {
            invokeVoid(ProviderKind.FRAME_GENERATION, current.frameGenerator(), current.frameGeneratorFailures(),
                    session -> session.reset(reason));
        }
        invokeVoid(ProviderKind.PRESENT_HOOK, current.presentHook(), current.presentHookFailures(),
                session -> session.reset(reason));
        upscalerSucceededThisFrame = true;
    }

    public ProviderResult invokeUpscaler(Function<UpscalerSession, ProviderResult> callback) {
        requireRenderThread();
        RuntimeState current = state;
        ProviderResult result = invoke(
                ProviderKind.UPSCALER, current.upscaler(), current.upscalerFailures(), callback,
                ProviderResult.skipped("upscaler_inactive", "No upscaler session is active"));
        upscalerSucceededThisFrame = current.upscaler().isEmpty()
                || result.code() == dev.mgf.api.provider.ProviderResultCode.SUCCESS;
        return result;
    }

    public ProviderResult invokeFrameGenerator(Function<FrameGenerationSession, ProviderResult> callback) {
        requireRenderThread();
        RuntimeState current = state;
        if (current.frameGeneratorDependsOnUpscaler() && !upscalerSucceededThisFrame) {
            return ProviderResult.skipped("upscaler_unavailable",
                    "Frame Generation was skipped because the upscaler did not produce a frame");
        }
        return invoke(
                ProviderKind.FRAME_GENERATION, current.frameGenerator(), current.frameGeneratorFailures(), callback,
                ProviderResult.skipped("frame_generation_inactive", "No Frame Generation session is active"));
    }

    public void present(Function<PresentHookSession, ProviderResult> beforePresent, Runnable vanillaPresent) {
        requireRenderThread();
        Objects.requireNonNull(beforePresent, "beforePresent");
        Objects.requireNonNull(vanillaPresent, "vanillaPresent");
        RuntimeState current = state;
        invoke(ProviderKind.PRESENT_HOOK, current.presentHook(), current.presentHookFailures(), beforePresent,
                ProviderResult.skipped("present_hook_inactive", "No PresentHook session is active"));
        vanillaPresent.run();
    }

    public void close() {
        requireRenderThread();
        closeSessions();
        selections = awaitingDeviceSelections(catalog, config);
        upscalerSucceededThisFrame = true;
    }

    private void closeSessions() {
        RuntimeState current = state;
        close(ProviderKind.PRESENT_HOOK, current.presentHook());
        close(ProviderKind.FRAME_GENERATION, current.frameGenerator());
        close(ProviderKind.UPSCALER, current.upscaler());
        state = RuntimeState.inactive();
    }

    private OpenedUpscaler openUpscaler(
            ProviderSelector.SelectedUpscaler selected, ProviderSessionContext context) {
        ProviderFailureTracker failures = new ProviderFailureTracker();
        if (selected.provider().isEmpty()) {
            return new OpenedUpscaler(Optional.empty(), failures, selected.diagnostic());
        }
        try {
            UpscalerSession session = Objects.requireNonNull(
                    selected.provider().orElseThrow().open(context), "upscaler session");
            return new OpenedUpscaler(Optional.of(session), failures, active(selected.diagnostic()));
        } catch (Throwable throwable) {
            failures.recordException(throwable);
            return new OpenedUpscaler(Optional.empty(), failures, disabled(selected.diagnostic(), failures));
        }
    }

    private OpenedFrameGenerator openFrameGenerator(
            ProviderSelector.SelectedFrameGenerator selected, ProviderSessionContext context) {
        ProviderFailureTracker failures = new ProviderFailureTracker();
        if (selected.provider().isEmpty()) {
            return new OpenedFrameGenerator(Optional.empty(), failures, selected.diagnostic());
        }
        try {
            FrameGenerationSession session = Objects.requireNonNull(
                    selected.provider().orElseThrow().open(context), "Frame Generation session");
            return new OpenedFrameGenerator(Optional.of(session), failures, active(selected.diagnostic()));
        } catch (Throwable throwable) {
            failures.recordException(throwable);
            return new OpenedFrameGenerator(Optional.empty(), failures, disabled(selected.diagnostic(), failures));
        }
    }

    private OpenedPresentHook openPresentHook(
            ProviderSelector.SelectedPresentHook selected, ProviderSessionContext context) {
        ProviderFailureTracker failures = new ProviderFailureTracker();
        if (selected.provider().isEmpty()) {
            return new OpenedPresentHook(Optional.empty(), failures, selected.diagnostic());
        }
        try {
            PresentHookSession session = Objects.requireNonNull(
                    selected.provider().orElseThrow().open(context), "PresentHook session");
            return new OpenedPresentHook(Optional.of(session), failures, active(selected.diagnostic()));
        } catch (Throwable throwable) {
            failures.recordException(throwable);
            return new OpenedPresentHook(Optional.empty(), failures, disabled(selected.diagnostic(), failures));
        }
    }

    private <T> ProviderResult invoke(
            ProviderKind kind,
            Optional<T> session,
            ProviderFailureTracker failures,
            Function<T, ProviderResult> callback,
            ProviderResult inactiveResult) {
        Objects.requireNonNull(callback, "callback");
        if (session.isEmpty() || failures.disabled()) {
            return inactiveResult;
        }
        ProviderResult result;
        try {
            result = failures.record(Objects.requireNonNull(callback.apply(session.orElseThrow()), "provider result"));
        } catch (Throwable throwable) {
            result = failures.recordException(throwable);
        }
        if (failures.disabled()) {
            disableDiagnostic(kind, failures);
        }
        return result;
    }

    private <T> void invokeVoid(
            ProviderKind kind,
            Optional<T> session,
            ProviderFailureTracker failures,
            ThrowingConsumer<T> callback) {
        if (session.isEmpty() || failures.disabled()) {
            return;
        }
        try {
            callback.accept(session.orElseThrow());
        } catch (Throwable throwable) {
            failures.recordException(throwable);
            disableDiagnostic(kind, failures);
        }
    }

    private void disableDiagnostic(ProviderKind kind, ProviderFailureTracker failures) {
        ProviderSelections current = selections;
        ProviderSelection changed = disabled(selection(current, kind), failures);
        selections = switch (kind) {
            case UPSCALER -> new ProviderSelections(changed,
                    state.frameGeneratorDependsOnUpscaler()
                            ? disabledByUpscaler(current.frameGeneration()) : current.frameGeneration(),
                    current.presentHook());
            case FRAME_GENERATION -> new ProviderSelections(current.upscaler(), changed, current.presentHook());
            case PRESENT_HOOK -> new ProviderSelections(current.upscaler(), current.frameGeneration(), changed);
        };
        MgfConstants.LOGGER.warn("{} provider disabled: {} ({})",
                kind, failures.message(), failures.reasonCode());
    }

    private static ProviderSelection selection(ProviderSelections selections, ProviderKind kind) {
        return switch (kind) {
            case UPSCALER -> selections.upscaler();
            case FRAME_GENERATION -> selections.frameGeneration();
            case PRESENT_HOOK -> selections.presentHook();
        };
    }

    private static ProviderSelection active(ProviderSelection selection) {
        return new ProviderSelection(selection.kind(), selection.registered(), selection.selected(),
                ProviderSessionState.ACTIVE, "active", "Provider session is active");
    }

    private static ProviderSelection disabled(
            ProviderSelection selection, ProviderFailureTracker failures) {
        return new ProviderSelection(selection.kind(), selection.registered(), selection.selected(),
                ProviderSessionState.DISABLED, failures.reasonCode(), failures.message());
    }

    private static ProviderSelection disabledByUpscaler(ProviderSelection selection) {
        return new ProviderSelection(selection.kind(), selection.registered(), selection.selected(),
                ProviderSessionState.DISABLED, "upscaler_unavailable",
                "Frame Generation was disabled because its selected upscaler failed");
    }

    private static ProviderSelections awaitingDeviceSelections(ProviderCatalog catalog, ProviderConfig config) {
        return new ProviderSelections(
                awaitingDevice(ProviderKind.UPSCALER,
                        catalog.upscalers().stream().map(provider -> provider.descriptor().id()).toList(),
                        config.upscaler()),
                awaitingDevice(ProviderKind.FRAME_GENERATION,
                        catalog.frameGenerators().stream().map(provider -> provider.descriptor().id()).toList(),
                        config.frameGeneration()),
                awaitingDevice(ProviderKind.PRESENT_HOOK,
                        catalog.presentHooks().stream().map(provider -> provider.descriptor().id()).toList(),
                        config.presentHook()));
    }

    private static ProviderSelection awaitingDevice(
            ProviderKind kind, List<ProviderId> registered, ProviderConfig.Choice choice) {
        if (choice.mode() == ProviderConfig.Mode.OFF) {
            return new ProviderSelection(kind, registered, Optional.empty(), ProviderSessionState.OFF,
                    choice.reasonCode(), choice.message());
        }
        if (registered.isEmpty()) {
            return new ProviderSelection(kind, registered, Optional.empty(), ProviderSessionState.UNSUPPORTED,
                    "no_provider", "No providers are registered");
        }
        return new ProviderSelection(kind, registered, Optional.empty(), ProviderSessionState.READY,
                "awaiting_device", "Providers will be probed after the graphics device is created");
    }

    private static void close(ProviderKind kind, Optional<? extends AutoCloseable> session) {
        if (session.isEmpty()) {
            return;
        }
        try {
            session.orElseThrow().close();
        } catch (Throwable throwable) {
            MgfConstants.LOGGER.error("{} provider close failed; continuing device shutdown", kind, throwable);
        }
    }

    private static void logSelections(String transition, ProviderSelections selections) {
        MgfConstants.LOGGER.info("Provider sessions {}: upscaler={}, frame_generation={}, present_hook={}",
                transition,
                selections.upscaler().state(),
                selections.frameGeneration().state(),
                selections.presentHook().state());
    }

    private void requireRenderThread() {
        if (!renderThread.getAsBoolean()) {
            throw new IllegalStateException("provider lifecycle must run on the render thread");
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    private record RuntimeState(
            Optional<UpscalerSession> upscaler,
            Optional<FrameGenerationSession> frameGenerator,
            Optional<PresentHookSession> presentHook,
            ProviderFailureTracker upscalerFailures,
            ProviderFailureTracker frameGeneratorFailures,
            ProviderFailureTracker presentHookFailures,
            boolean frameGeneratorDependsOnUpscaler) {

        static RuntimeState inactive() {
            return new RuntimeState(Optional.empty(), Optional.empty(), Optional.empty(),
                    new ProviderFailureTracker(), new ProviderFailureTracker(), new ProviderFailureTracker(), false);
        }

        boolean active() {
            return upscaler.isPresent() || frameGenerator.isPresent() || presentHook.isPresent();
        }
    }

    private record OpenedUpscaler(
            Optional<UpscalerSession> session,
            ProviderFailureTracker failures,
            ProviderSelection diagnostic) {
    }

    private record OpenedFrameGenerator(
            Optional<FrameGenerationSession> session,
            ProviderFailureTracker failures,
            ProviderSelection diagnostic) {
    }

    private record OpenedPresentHook(
            Optional<PresentHookSession> session,
            ProviderFailureTracker failures,
            ProviderSelection diagnostic) {
    }
}
