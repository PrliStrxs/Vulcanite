package dev.mgf.api.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mgf.api.GraphicsBackendKind;

final class ProviderContractsTest {

    @Test
    void validatesProviderIdentityAndMetadata() {
        ProviderId id = new ProviderId("example:dlss");
        assertEquals("example:dlss", id.value());
        assertEquals("example:dlss", id.toString());
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("DLSS"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("example:"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("example:bad value"));

        ProviderDescriptor descriptor = new ProviderDescriptor(id, "Example DLSS", "1.2.3", 100, 0, 3);
        assertEquals(id, descriptor.id());
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderDescriptor(id, " ", "1.0", 0, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderDescriptor(id, "Example", " ", 0, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderDescriptor(id, "Example", "1.0", 0, -1, 3));
    }

    @Test
    void validatesSupportAndFrameResults() {
        assertTrue(ProviderSupport.available().supported());
        ProviderSupport unsupported = ProviderSupport.unsupported("missing_motion", "Motion vectors are unavailable");
        assertFalse(unsupported.supported());
        assertEquals("missing_motion", unsupported.reasonCode());

        assertEquals(ProviderResultCode.SUCCESS, ProviderResult.success().code());
        assertEquals(ProviderResultCode.SKIPPED,
                ProviderResult.skipped("history_reset", "History is not valid").code());
        assertEquals(ProviderResultCode.RECOVERABLE_FAILURE,
                ProviderResult.recoverable("sdk_busy", "SDK is busy").code());
        assertEquals(ProviderResultCode.FATAL_FAILURE,
                ProviderResult.fatal("device_lost", "Device was lost").code());
        assertThrows(IllegalArgumentException.class,
                () -> ProviderResult.recoverable("", "message"));
    }

    @Test
    void validatesFrameAndResourceDescriptors() {
        FrameDimensions dimensions = new FrameDimensions(1280, 720, 1920, 1080);
        assertEquals(1280, dimensions.renderWidth());
        assertThrows(IllegalArgumentException.class,
                () -> new FrameDimensions(0, 720, 1920, 1080));

        Matrix4 identity = Matrix4.identity();
        assertEquals(1.0F, identity.m00());
        assertEquals(1.0F, identity.m33());
        FrameMatrices matrices = new FrameMatrices(identity, identity, identity, identity);
        assertEquals(identity, matrices.currentViewProjection());

        FrameInfo frame = new FrameInfo(7, 0.016, true, 2, 3);
        assertEquals(7, frame.frameId());
        assertThrows(IllegalArgumentException.class,
                () -> new FrameInfo(-1, 0.016, false, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new FrameInfo(1, Double.NaN, false, 1, 1));

        ImageState state = new ImageState(1, 2, 4, -1);
        BorrowedImage image = new BorrowedImage(10, 11, 1920, 1080, 37, 7,
                ColorEncoding.SRGB, state, ImageOwnership.MGF,
                ImageLifetime.CALLBACK, 2, 3);
        assertEquals(10, image.imageHandle());
        assertThrows(IllegalArgumentException.class,
                () -> new BorrowedImage(0, 0, 1, 1, 37, 0,
                        ColorEncoding.SRGB, state, ImageOwnership.MGF,
                        ImageLifetime.CALLBACK, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandRecordingContext(0, 0, 1, 1));
    }

    @Test
    void copiesEnvironmentAndSelectionCollections() {
        Set<FrameResourceKind> resources = new java.util.HashSet<>();
        resources.add(FrameResourceKind.COLOR);
        ProviderEnvironment environment = new ProviderEnvironment(
                GraphicsBackendKind.VULKAN, 2, Optional.empty(), resources, true);
        resources.add(FrameResourceKind.MOTION_VECTORS);
        assertEquals(Set.of(FrameResourceKind.COLOR), environment.availableResources());

        ProviderId id = new ProviderId("example:upscaler");
        List<ProviderId> registered = new java.util.ArrayList<>(List.of(id));
        ProviderSelection selection = new ProviderSelection(
                ProviderKind.UPSCALER, registered, Optional.of(id),
                ProviderSessionState.ACTIVE, "selected", "Provider selected");
        registered.clear();
        assertEquals(List.of(id), selection.registered());

        ProviderSelections selections = new ProviderSelections(
                selection,
                ProviderSelection.off(ProviderKind.FRAME_GENERATION),
                ProviderSelection.off(ProviderKind.PRESENT_HOOK));
        assertEquals(selection, selections.upscaler());
    }
}
