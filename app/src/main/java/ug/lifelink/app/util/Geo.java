package ug.lifelink.app.util;

/**
 * Distance between two places on Earth. The haversine formula treats the Earth as a sphere,
 * which is accurate to well under one percent, plenty for "is this hospital within 30 km".
 * The result is straight-line distance, not road distance.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static boolean isValidLatitude(double lat) {
        return !Double.isNaN(lat) && lat >= -90.0 && lat <= 90.0;
    }

    public static boolean isValidLongitude(double lng) {
        return !Double.isNaN(lng) && lng >= -180.0 && lng <= 180.0;
    }
}
