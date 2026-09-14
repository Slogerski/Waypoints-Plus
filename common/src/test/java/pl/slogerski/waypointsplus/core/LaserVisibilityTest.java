package pl.slogerski.waypointsplus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaserVisibilityTest {
    @Test void limitsHorizontalDistanceTo250Blocks() {
        LaserVisibility view = LaserVisibility.fromCamera(0, 0, 1000, 70, -500, -64, 320);
        assertTrue(view.isPotentiallyVisible(1000, -250));
        assertFalse(view.isPotentiallyVisible(1000, -249.99));
        assertTrue(view.isPotentiallyVisible(1150, -300));
        assertFalse(view.isPotentiallyVisible(1200, -300));
    }

    @Test void keepsBeamAboveCameraWhenLabelIsBelow() {
        assertTrue(LaserVisibility.fromCamera(0, -90, 0, 100, 0, -64, 320)
                .isPotentiallyVisible(0, 10));
    }

    @Test void keepsBeamBelowCameraWhenLabelIsAbove() {
        assertTrue(LaserVisibility.fromCamera(0, 90, 0, 100, 0, -64, 320)
                .isPotentiallyVisible(0, 10));
    }

    @Test void rejectsWholeBeamBehindCamera() {
        assertFalse(LaserVisibility.fromCamera(0, 0, 0, 100, 0, -64, 320)
                .isPotentiallyVisible(0, -10));
        assertTrue(LaserVisibility.fromCamera(0, 0, 0, 100, 0, -64, 320)
                .isPotentiallyVisible(0, 10));
    }

    @Test void includesBeamWidthAtCameraPlane() {
        assertTrue(LaserVisibility.fromCamera(0, 0, 0, 100, 0, -64, 320)
                .isPotentiallyVisible(0, -0.04));
    }

    @Test void respectsCustomHeightAndCameraOutsideWorld() {
        assertFalse(LaserVisibility.fromCamera(0, -90, 0, 500, 0, 0, 256)
                .isPotentiallyVisible(0, 10));
        assertTrue(LaserVisibility.fromCamera(0, -90, 0, 500, 0, -128, 1024)
                .isPotentiallyVisible(0, 10));
        assertFalse(LaserVisibility.fromCamera(0, 90, 0, -200, 0, -128, 1024)
                .isPotentiallyVisible(0, 10));
    }

    @Test void keepsAnyBeamWithAnEndpointInFrontForAllCameraAngles() {
        for (int yaw = -180; yaw <= 180; yaw += 15) {
            for (int pitch = -90; pitch <= 90; pitch += 10) {
                LaserVisibility view = LaserVisibility.fromCamera(yaw, pitch, 120, 70, -300, -64, 320);
                double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                for (int dx = -100; dx <= 100; dx += 25) {
                    for (int dz = -100; dz <= 100; dz += 25) {
                        double horizontal = dx * -Math.sin(y) * Math.cos(p) + dz * Math.cos(y) * Math.cos(p);
                        if (horizontal + (-64 - 70) * -Math.sin(p) >= 0
                                || horizontal + (320 - 70) * -Math.sin(p) >= 0) {
                            assertTrue(view.isPotentiallyVisible(120 + dx, -300 + dz));
                        }
                    }
                }
            }
        }
    }
}
