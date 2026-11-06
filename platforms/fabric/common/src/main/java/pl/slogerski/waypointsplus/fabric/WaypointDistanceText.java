package pl.slogerski.waypointsplus.fabric;

final class WaypointDistanceText {
    private long bucket = Long.MIN_VALUE;
    private boolean kilometers;
    private boolean polish;
    private String text = "";

    boolean update(double distance, boolean metersOnly, boolean polish) {
        boolean km = !metersOnly && distance >= 1000;
        long nextBucket = Math.round(km ? distance / 100 : distance);
        if (bucket == nextBucket && kilometers == km && this.polish == polish) return false;
        bucket = nextBucket;
        kilometers = km;
        this.polish = polish;
        text = km ? bucket / 10 + (polish ? "," : ".") + bucket % 10 + " km" : bucket + " m";
        return true;
    }

    String text() {
        return text;
    }
}
