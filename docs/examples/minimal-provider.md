# Minimal Provider

The source below is compiled by `:samples:sample-provider:compileJava` and
depends only on `mgf-api` and Fabric Loader. It registers all three roles but
reports them unsupported until the author implements real SDK probing and
recording.

Source: [`samples/sample-provider/src/main/java/dev/mgf/samples/provider/MinimalProvider.java`](../../samples/sample-provider/src/main/java/dev/mgf/samples/provider/MinimalProvider.java)

```java
package dev.mgf.samples.provider;

import java.util.Optional;

import dev.mgf.api.framegen.FrameGenerationFrame;
import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.framegen.FrameGenerationSession;
import dev.mgf.api.framegen.FrameGenerationSupport;
import dev.mgf.api.present.PresentFrame;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.present.PresentHookSession;
import dev.mgf.api.present.PresentHookSupport;
import dev.mgf.api.present.PresentReceipt;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.MgfProviderRegistrar;
import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderRegistry;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerSession;
import dev.mgf.api.upscale.UpscalerSupport;

/** Minimal registration and lifecycle skeleton for all Vulcanite provider roles. */
public final class MinimalProvider implements MgfProviderRegistrar {

    @Override
    public void registerProviders(ProviderRegistry registry) {
        registry.registerUpscaler(new MinimalUpscaler());
        registry.registerFrameGenerator(new MinimalFrameGenerator());
        registry.registerPresentHook(new MinimalPresentHook());
    }

    private static ProviderDescriptor descriptor(String path, String name) {
        return new ProviderDescriptor(
                new ProviderId("example:" + path), name, "1.0.0", 0, 0, 3);
    }

    private static final class MinimalUpscaler implements UpscalerProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return MinimalProvider.descriptor("upscaler", "Example Upscaler");
        }

        @Override
        public UpscalerSupport probe(ProviderEnvironment environment) {
            return UpscalerSupport.unavailable(
                    "not_implemented", "Replace this probe with backend and SDK checks");
        }

        @Override
        public UpscalerSession open(ProviderSessionContext context) {
            return new UpscalerSession() {
                @Override public void resize(FrameDimensions dimensions) { }
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult record(UpscaleFrame frame) {
                    return skipped();
                }
                @Override public void close() { }
            };
        }
    }

    private static final class MinimalFrameGenerator implements FrameGenerationProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return MinimalProvider.descriptor("frame-generator", "Example Frame Generator");
        }

        @Override
        public FrameGenerationSupport probe(
                ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler) {
            return FrameGenerationSupport.unavailable(
                    "not_implemented", "Replace this probe with backend and SDK checks");
        }

        @Override
        public FrameGenerationSession open(ProviderSessionContext context) {
            return new FrameGenerationSession() {
                @Override public void resize(FrameDimensions dimensions) { }
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult record(FrameGenerationFrame frame) {
                    return skipped();
                }
                @Override public void close() { }
            };
        }
    }

    private static final class MinimalPresentHook implements PresentHookProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return MinimalProvider.descriptor("present-hook", "Example Present Hook");
        }

        @Override
        public PresentHookSupport probe(
                ProviderEnvironment environment, Optional<ProviderId> selectedFrameGenerator) {
            return PresentHookSupport.unavailable(
                    "not_implemented", "Replace this probe with backend and SDK checks");
        }

        @Override
        public PresentHookSession open(ProviderSessionContext context) {
            return new PresentHookSession() {
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult beforePresent(PresentFrame frame) {
                    return skipped();
                }
                @Override public void afterPresent(PresentReceipt receipt) { }
                @Override public void close() { }
            };
        }
    }

    private static ProviderResult skipped() {
        return ProviderResult.skipped("not_implemented", "Example provider records no work");
    }
}
```

The corresponding `fabric.mod.json` entrypoint is:

```json
"entrypoints": {
  "mgf:providers": [
    "dev.mgf.samples.provider.MinimalProvider"
  ]
}
```

Returning an unavailable probe is deliberate. Change a role to available only
after its required inputs, device/SDK support, output writes, reset behavior,
failure results, and shutdown are implemented and validated.
