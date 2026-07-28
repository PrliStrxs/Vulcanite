package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.ImageLifetime;
import dev.mgf.api.provider.ResetReason;

final class VulkanProviderResourcesTest {

    private static final FrameDimensions FULL_HD = new FrameDimensions(1920, 1080, 1920, 1080);
    private static final FrameDimensions QHD = new FrameDimensions(2560, 1440, 2560, 1440);

    @Test
    void adapterOwnedImageDescriptorsAreCallbackScoped() {
        assertEquals(ImageLifetime.CALLBACK, VulkanProviderImage.DESCRIPTOR_LIFETIME);
    }

    @Test
    void unchangedDimensionsReuseResourcesAndGeneration() {
        Fixture fixture = new Fixture();

        assertTrue(fixture.resources.ensure(FULL_HD));
        FakeImage output = fixture.resources.upscaledOutput();
        long generation = fixture.resources.resourceGeneration();
        fixture.resources.markHistoryValid();

        assertFalse(fixture.resources.ensure(FULL_HD));

        assertSame(output, fixture.resources.upscaledOutput());
        assertEquals(generation, fixture.resources.resourceGeneration());
        assertEquals(4, fixture.created.size());
        assertTrue(fixture.resources.historyValid());
        assertTrue(fixture.retired.isEmpty());
    }

    @Test
    void changedDimensionsIncrementGenerationAndInvalidateHistory() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.resources.ensure(FULL_HD));
        fixture.resources.markHistoryValid();
        long firstGeneration = fixture.resources.resourceGeneration();

        assertTrue(fixture.resources.ensure(QHD));

        assertEquals(firstGeneration + 1, fixture.resources.resourceGeneration());
        assertEquals(QHD, fixture.resources.dimensions());
        assertFalse(fixture.resources.historyValid());
    }

    @Test
    void resetInvalidatesHistoryWithoutReplacingImages() {
        Fixture fixture = new Fixture();
        fixture.resources.ensure(FULL_HD);
        fixture.resources.markHistoryValid();
        FakeImage output = fixture.resources.upscaledOutput();

        fixture.resources.reset(ResetReason.WORLD_CHANGE);

        assertFalse(fixture.resources.historyValid());
        assertSame(output, fixture.resources.upscaledOutput());
        assertEquals(1, fixture.resources.resourceGeneration());
    }

    @Test
    void retiredImagesAreDestroyedOnlyByDeferredCallback() {
        Fixture fixture = new Fixture();
        fixture.resources.ensure(FULL_HD);
        List<FakeImage> firstGeneration = List.copyOf(fixture.created);

        fixture.resources.ensure(QHD);

        assertEquals(1, fixture.retired.size());
        assertTrue(firstGeneration.stream().noneMatch(FakeImage::destroyed));

        fixture.retired.getFirst().run();

        assertTrue(firstGeneration.stream().allMatch(FakeImage::destroyed));
        assertTrue(fixture.created.subList(4, 8).stream().noneMatch(FakeImage::destroyed));
    }

    private static final class Fixture {
        private final List<FakeImage> created = new ArrayList<>();
        private final List<Runnable> retired = new ArrayList<>();
        private final VulkanProviderResources<FakeImage> resources = new VulkanProviderResources<>(
                7,
                (label, width, height) -> {
                    FakeImage image = new FakeImage(label, width, height);
                    created.add(image);
                    return image;
                },
                retired::add);
    }

    private static final class FakeImage implements VulkanProviderResources.OwnedImage {
        private final String label;
        private final int width;
        private final int height;
        private boolean destroyed;

        private FakeImage(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        boolean destroyed() {
            return destroyed;
        }

        @Override
        public String toString() {
            return label + "[" + width + "x" + height + "]";
        }
    }
}
