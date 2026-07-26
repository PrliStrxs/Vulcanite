package dev.mgf.samples.interop;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.framegraph.FramePass;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphEvents;

/**
 * M2 verification probe: injects a no-op pass at BEFORE_EXECUTE every frame
 * and logs once when it actually executes on the GPU timeline, plus logs the
 * first firing of each per-pass anchor. Load a world to see the log lines —
 * the frame graph does not run on the title screen.
 */
public final class SampleFrameGraphProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Sample-Interop");
    private static final AtomicLong EXECUTIONS = new AtomicLong();

    private SampleFrameGraphProbe() {
    }

    static void install() {
        for (FrameGraphAnchor anchor : FrameGraphAnchor.values()) {
            FrameGraphEvents.register(anchor, context -> logFirst(anchor));
        }

        FrameGraphEvents.register(FrameGraphAnchor.BEFORE_EXECUTE, context -> {
            FramePass pass = context.builder().addPass("mgf_sample_probe");
            pass.disableCulling(); // no reads/writes — would be culled otherwise
            pass.executes(() -> {
                if (EXECUTIONS.incrementAndGet() == 1L) {
                    LOGGER.info("Injected frame-graph pass executed (targets: main={} translucent={})",
                            context.targets().main != null,
                            context.targets().translucent != null);
                }
            });
        });
    }

    private static void logFirst(FrameGraphAnchor anchor) {
        // EnumSet-free simple guard: log spam is prevented by checking counts.
        if (FIRST_LOGGED.add(anchor)) {
            LOGGER.info("Frame-graph anchor fired: {}", anchor);
        }
    }

    private static final java.util.Set<FrameGraphAnchor> FIRST_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
}
