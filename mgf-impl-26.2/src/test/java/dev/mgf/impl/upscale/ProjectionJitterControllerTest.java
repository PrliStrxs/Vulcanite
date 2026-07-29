package dev.mgf.impl.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ProjectionJitterControllerTest {

    @Test
    void emitsDeterministicHaltonSamplesAndResets() {
        ProjectionJitterController controller = new ProjectionJitterController(8);

        var first = controller.next();
        var second = controller.next();

        assertEquals(0, first.index());
        assertEquals(8, first.period());
        assertNotEquals(first.offsetY(), second.offsetY());

        controller.reset();
        assertEquals(first, controller.next());
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectionJitterController(0));
    }
}
