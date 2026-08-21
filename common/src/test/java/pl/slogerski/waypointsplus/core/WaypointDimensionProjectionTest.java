package pl.slogerski.waypointsplus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointDimensionProjectionTest {
    @Test void keepsExactDimensionVisibleWhenDisabled() {
        assertEquals(1.0, WaypointDimensionProjection.scale("custom:lobby", "custom:lobby", false));
    }

    @Test void hidesDifferentDimensionsWhenDisabled() {
        assertTrue(Double.isNaN(WaypointDimensionProjection.scale(
                "minecraft:overworld", "minecraft:the_nether", false)));
    }

    @Test void convertsBetweenSurfaceDimensionsAndNether() {
        assertEquals(8.0, WaypointDimensionProjection.scale(
                "custom:lobby", "minecraft:the_nether", true));
        assertEquals(0.125, WaypointDimensionProjection.scale(
                "minecraft:the_nether", "custom:lobby", true));
    }

    @Test void sharesCoordinatesBetweenSurfaceDimensions() {
        assertEquals(1.0, WaypointDimensionProjection.scale(
                "minecraft:overworld", "custom:lobby", true));
        assertEquals(1.0, WaypointDimensionProjection.scale(
                "custom:lobby", "minecraft:overworld", true));
    }

    @Test void keepsEndSeparate() {
        assertTrue(Double.isNaN(WaypointDimensionProjection.scale(
                "minecraft:the_end", "minecraft:overworld", true)));
        assertTrue(Double.isNaN(WaypointDimensionProjection.scale(
                "custom:lobby", "minecraft:the_end", true)));
    }
}
