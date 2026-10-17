package ug.lifelink.app.model;

/**
 * One record owned by one user. A single class serves both personas:
 * a donor's record is a DONATION, a hospital user's record is a blood REQUEST.
 * Sharing one shape means one list layout, one adapter and one CRUD form later.
 */
public class BloodRecord {

    public static final String KIND_DONATION = "DONATION";
    public static final String KIND_REQUEST = "REQUEST";

    public static final String STATUS_COMPLETED = "COMPLETED"; // donation finished
    public static final String STATUS_OPEN = "OPEN";           // request waiting for donors
    public static final String STATUS_URGENT = "URGENT";       // request needing donors now
    public static final String STATUS_FULFILLED = "FULFILLED"; // request met

    private final long id;
    private final long userId;
    private final String kind;
    private final String bloodGroup;
    private final int units;
    private final String facility;
    private final String status;
    private final String note;
    private final long createdAt;

    public BloodRecord(long id, long userId, String kind, String bloodGroup, int units,
                       String facility, String status, String note, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.kind = kind;
        this.bloodGroup = bloodGroup;
        this.units = units;
        this.facility = facility;
        this.status = status;
        this.note = note;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public int getUnits() {
        return units;
    }

    public String getFacility() {
        return facility;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
