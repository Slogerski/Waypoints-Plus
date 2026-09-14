package pl.slogerski.waypointsplus.core;

public record LaserVisibility(double cameraX, double cameraZ, double forwardX,
                              double forwardZ, double depthAllowance) {
    public static final float HALF_WIDTH = 0.055f;
    private static final double MAX_DISTANCE_SQUARED = 250.0 * 250.0;

    public static LaserVisibility fromCamera(float yawDegrees, float pitchDegrees,
                                              double cameraX, double cameraY, double cameraZ,
                                              int bottomY, int topY) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        double forwardX = -Math.sin(yaw) * cosPitch;
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(yaw) * cosPitch;
        double verticalDepth = Math.max((bottomY - cameraY) * forwardY,
                (topY - cameraY) * forwardY);
        double widthMargin = HALF_WIDTH * (Math.abs(forwardX) + Math.abs(forwardZ));
        return new LaserVisibility(cameraX, cameraZ, forwardX, forwardZ,
                verticalDepth + widthMargin + 0.01);
    }

    public boolean isPotentiallyVisible(double x, double z) {
        double dx = x - cameraX;
        double dz = z - cameraZ;
        return dx * dx + dz * dz <= MAX_DISTANCE_SQUARED
                && dx * forwardX + dz * forwardZ + depthAllowance >= 0;
    }
}
