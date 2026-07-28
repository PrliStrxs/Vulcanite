package dev.mgf.impl.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.framegen.FrameGenerationSupport;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.present.PresentHookSupport;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderKind;
import dev.mgf.api.provider.ProviderSelection;
import dev.mgf.api.provider.ProviderSessionState;
import dev.mgf.api.provider.ProviderSupport;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerSupport;

/** Deterministic, fail-soft provider arbitration. */
public final class ProviderSelector {

    private static final Comparator<Object> PRIORITY = Comparator
            .comparingInt((Object value) -> descriptor(value).priority())
            .reversed()
            .thenComparing(value -> descriptor(value).id());

    private ProviderSelector() {
    }

    public static SelectedUpscaler selectUpscaler(
            ProviderCatalog catalog,
            ProviderConfig.Choice choice,
            ProviderEnvironment environment) {
        List<UpscalerProvider> providers = catalog.upscalers();
        Selection<UpscalerProvider, UpscalerSupport> selected = select(
                ProviderKind.UPSCALER, providers, choice,
                provider -> provider.probe(environment),
                support -> support.status(),
                support -> support.requirements().orElseThrow().requiredResources(),
                environment);
        return new SelectedUpscaler(selected.provider, selected.support, selected.diagnostic);
    }

    public static SelectedFrameGenerator selectFrameGenerator(
            ProviderCatalog catalog,
            ProviderConfig.Choice choice,
            ProviderEnvironment environment,
            Optional<ProviderId> selectedUpscaler) {
        List<FrameGenerationProvider> providers = catalog.frameGenerators();
        Selection<FrameGenerationProvider, FrameGenerationSupport> selected = select(
                ProviderKind.FRAME_GENERATION, providers, choice,
                provider -> provider.probe(environment, selectedUpscaler),
                support -> support.status(),
                support -> support.requirements().orElseThrow().requiredResources(),
                environment);
        return new SelectedFrameGenerator(selected.provider, selected.support, selected.diagnostic);
    }

    public static SelectedPresentHook selectPresentHook(
            ProviderCatalog catalog,
            ProviderConfig.Choice choice,
            ProviderEnvironment environment,
            Optional<ProviderId> selectedFrameGenerator) {
        List<PresentHookProvider> providers = catalog.presentHooks();
        Selection<PresentHookProvider, PresentHookSupport> selected = select(
                ProviderKind.PRESENT_HOOK, providers, choice,
                provider -> provider.probe(environment, selectedFrameGenerator),
                support -> support.status(),
                support -> java.util.Set.of(),
                environment);
        if (selected.provider.isPresent() && selectedFrameGenerator.isPresent()
                && !selected.support.orElseThrow().capabilities().orElseThrow().supportsGeneratedFrames()) {
            return new SelectedPresentHook(Optional.empty(), Optional.empty(), diagnostic(
                    ProviderKind.PRESENT_HOOK, providers, Optional.empty(),
                    ProviderSessionState.UNSUPPORTED, "generated_frames_unsupported",
                    "Present hook does not accept generated frames"));
        }
        return new SelectedPresentHook(selected.provider, selected.support, selected.diagnostic);
    }

    private static <T, S> Selection<T, S> select(
            ProviderKind kind,
            List<T> providers,
            ProviderConfig.Choice choice,
            Function<T, S> probe,
            Function<S, ProviderSupport> status,
            Function<S, java.util.Set<FrameResourceKind>> requirements,
            ProviderEnvironment environment) {
        Objects.requireNonNull(choice, "choice");
        if (choice.mode() == ProviderConfig.Mode.OFF) {
            return new Selection<>(Optional.empty(), Optional.empty(), diagnostic(
                    kind, providers, Optional.empty(), ProviderSessionState.OFF,
                    choice.reasonCode(), choice.message()));
        }

        List<T> candidates;
        if (choice.mode() == ProviderConfig.Mode.EXACT) {
            ProviderId requested = choice.providerId().orElseThrow();
            candidates = providers.stream()
                    .filter(provider -> descriptor(provider).id().equals(requested))
                    .toList();
            if (candidates.isEmpty()) {
                return new Selection<>(Optional.empty(), Optional.empty(), diagnostic(
                        kind, providers, Optional.empty(), ProviderSessionState.UNSUPPORTED,
                        "missing_provider", "Configured provider is not registered: " + requested));
            }
        } else {
            candidates = providers.stream().sorted(PRIORITY).toList();
        }

        ProviderSupport lastFailure = null;
        for (T provider : candidates) {
            S probed;
            try {
                probed = Objects.requireNonNull(probe.apply(provider), "provider probe result");
            } catch (Throwable throwable) {
                lastFailure = ProviderSupport.unsupported("provider_exception",
                        throwable.getClass().getSimpleName() + describeMessage(throwable));
                continue;
            }
            ProviderSupport support = status.apply(probed);
            if (!support.supported()) {
                lastFailure = support;
                continue;
            }
            java.util.Set<FrameResourceKind> missing = new java.util.HashSet<>(requirements.apply(probed));
            missing.removeAll(environment.availableResources());
            if (!missing.isEmpty()) {
                lastFailure = ProviderSupport.unsupported("missing_resources",
                        "Required frame resources are unavailable: " + missing);
                continue;
            }
            ProviderId id = descriptor(provider).id();
            return new Selection<>(Optional.of(provider), Optional.of(probed), diagnostic(
                    kind, providers, Optional.of(id), ProviderSessionState.READY,
                    "selected", "Selected provider " + id));
        }

        ProviderSupport failure = lastFailure == null
                ? ProviderSupport.unsupported("no_provider", "No providers are registered")
                : lastFailure;
        return new Selection<>(Optional.empty(), Optional.empty(), diagnostic(
                kind, providers, Optional.empty(), ProviderSessionState.UNSUPPORTED,
                failure.reasonCode(), failure.message()));
    }

    private static ProviderSelection diagnostic(
            ProviderKind kind,
            List<?> providers,
            Optional<ProviderId> selected,
            ProviderSessionState state,
            String reasonCode,
            String message) {
        return new ProviderSelection(kind,
                providers.stream().map(ProviderSelector::descriptor).map(ProviderDescriptor::id).toList(),
                selected, state, reasonCode, message);
    }

    private static ProviderDescriptor descriptor(Object provider) {
        if (provider instanceof UpscalerProvider upscaler) {
            return upscaler.descriptor();
        }
        if (provider instanceof FrameGenerationProvider frameGenerator) {
            return frameGenerator.descriptor();
        }
        if (provider instanceof PresentHookProvider presentHook) {
            return presentHook.descriptor();
        }
        throw new IllegalArgumentException("unknown provider type: " + provider);
    }

    private static String describeMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "" : ": " + throwable.getMessage();
    }

    private record Selection<T, S>(
            Optional<T> provider,
            Optional<S> support,
            ProviderSelection diagnostic) {
    }

    public record SelectedUpscaler(
            Optional<UpscalerProvider> provider,
            Optional<UpscalerSupport> support,
            ProviderSelection diagnostic) {
    }

    public record SelectedFrameGenerator(
            Optional<FrameGenerationProvider> provider,
            Optional<FrameGenerationSupport> support,
            ProviderSelection diagnostic) {
    }

    public record SelectedPresentHook(
            Optional<PresentHookProvider> provider,
            Optional<PresentHookSupport> support,
            ProviderSelection diagnostic) {
    }
}
