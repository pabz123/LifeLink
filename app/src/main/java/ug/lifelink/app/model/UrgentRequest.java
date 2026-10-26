package ug.lifelink.app.model;

/**
 * An urgent hospital request for blood, as published in the shared request list (Firebase
 * Realtime Database, read over REST in Milestone 4) and kept on the phone in the request cache.
 *
 * The request holds no personal data about donors. The distance is worked out on the phone from
 * the phone's own location, so a donor's location never leaves the device.
 *
 * Immutable: withDistance() returns a copy with the distance filled in.
 */
public final class UrgentRequest {

    private final String remoteId;
    private final String facility;
    private final String bloodGroup;
    private final int units;
    private final String urgency;      // Urgency.CRITICAL, URGENT or ROUTINE
    private final String note;
    private final String contactPhone;
    private final double lat;          // NaN when the hospital's position is not known
    private final double lng;
    private final boolean verified;    // an administrator has confirmed the hospital
    private final long postedAt;       // epoch millis, 0 when unknown
    private final long expiresAt;      // epoch millis, 0 means no expiry
    private final double distanceKm;   // NaN until computed from the donor's location

    public UrgentRequest(String remoteId, String facility, String bloodGroup, int units,
                         String urgency, String note, String contactPhone,
                         double lat, double lng, boolean verified,
                         long postedAt, long expiresAt, double distanceKm) {
        this.remoteId = remoteId;
        this.facility = facility;
        this.bloodGroup = bloodGroup;
        this.units = units;
        this.urgency = urgency;
        this.note = note == null ? "" : note;
        this.contactPhone = contactPhone == null ? "" : contactPhone;
        this.lat = lat;
        this.lng = lng;
        this.verified = verified;
        this.postedAt = postedAt;
        this.expiresAt = expiresAt;
        this.distanceKm = distanceKm;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public String getFacility() {
        return facility;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public int getUnits() {
        return units;
    }

    public String getUrgency() {
        return urgency;
    }

    public String getNote() {
        return note;
    }

    /** Number the donor can dial, or an empty string when the hospital gave none. */
    public String getContactPhone() {
        return contactPhone;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public boolean hasLocation() {
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    public boolean isVerified() {
        return verified;
    }

    public long getPostedAt() {
        return postedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    /** Distance from the donor in kilometres, or NaN when it could not be worked out. */
    public double getDistanceKm() {
        return distanceKm;
    }

    public boolean hasDistance() {
        return !Double.isNaN(distanceKm);
    }

    public boolean isExpired(long nowMillis) {
        return expiresAt > 0 && expiresAt <= nowMillis;
    }

    public UrgentRequest withDistance(double km) {
        return new UrgentRequest(remoteId, facility, bloodGroup, units, urgency, note,
                contactPhone, lat, lng, verified, postedAt, expiresAt, km);
    }
}
