package dev.mgf.samples.interop;

import dev.mgf.api.unstable.compute.ComputeEffects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** M4 visible sample: Vulkan luminance-histogram auto exposure. */
public final class SampleAutoExposure {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Sample-Interop");

    private SampleAutoExposure() {
    }

    static void install() {
        ComputeEffects.registerMainColorAutoExposure("sample_auto_exposure");
        if (Boolean.getBoolean("mgf.sample.autoStopAfterExposure")) {
            int[] worldTicks = {0};
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.level == null) {
                    return;
                }
                int tick = ++worldTicks[0];
                if (tick == 30) {
                    client.getWindow().setWindowed(960, 540);
                } else if (tick == 60) {
                    client.reloadResourcePacks().whenComplete((unused, error) -> {
                        if (error == null) {
                            LOGGER.info("M4 world smoke resource reload completed");
                        } else {
                            LOGGER.error("M4 world smoke resource reload failed", error);
                        }
                    });
                } else if (tick == 120) {
                    client.getWindow().setWindowed(854, 480);
                } else if (tick >= 180) {
                    client.stop();
                }
            });
        }
    }
}
