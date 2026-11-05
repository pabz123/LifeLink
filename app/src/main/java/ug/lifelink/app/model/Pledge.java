package ug.lifelink.app.model;

/**
 * A donation a donor plans to make. It lives only on this phone. It is not a donation record:
 * a pledge says "I intend to give on this day", and only the donor can mark it done.
 */
public final class Pledge {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_COMPLETED = "COMPLETED"; // the donor says they gave
    public static final String STATUS_MISSED = "MISSED";       // shown when a planned day has passed
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final long id;
    private final long userId;
    private final String facility;
    private final String plannedDate; // ISO yyyy-MM-dd
    private final String status;
    private final long createdAt;

    public Pledge(long id, long userId, String facility, String plannedDate, String status,
                  long createdAt) {
        this.id = id;
        this.userId = userId;
        this.facility = facility;
        this.plannedDate = plannedDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public String getFacility() { return facility; }
    public String getPlannedDate() { return plannedDate; }
    public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
}
