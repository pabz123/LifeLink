package ug.lifelink.app.model;

/**
 * A donation a donor plans to make. It is not a donation record: a pledge says "I intend to give
 * on this day", and only the donor can mark it done. A pledge stays on the phone unless the donor
 * ticked the consent box; then a copy is also kept in the shared database so the hospital can
 * expect the donor, and the hospital can confirm it.
 */
public final class Pledge {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_COMPLETED = "COMPLETED"; // the donor says they gave
    public static final String STATUS_MISSED = "MISSED";       // shown when a planned day has passed
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** What the hospital may set on the shared copy. */
    public static final String REMOTE_CONFIRMED = "CONFIRMED";
    public static final String REMOTE_DID_NOT_ATTEND = "DID_NOT_ATTEND";

    private final long id;
    private final long userId;
    private final String facility;
    private final String plannedDate; // ISO yyyy-MM-dd
    private final String status;
    private final long createdAt;
    private final String remoteId;     // null when never shared
    private final String code;         // the short code the donor shows at the hospital
    private final boolean shared;      // the donor agreed to share this pledge with the hospital
    private final boolean synced;      // the shared copy is up to date
    private final String remoteStatus; // what the hospital set, or null

    public Pledge(long id, long userId, String facility, String plannedDate, String status,
                  long createdAt, String remoteId, String code, boolean shared, boolean synced,
                  String remoteStatus) {
        this.id = id;
        this.userId = userId;
        this.facility = facility;
        this.plannedDate = plannedDate;
        this.status = status;
        this.createdAt = createdAt;
        this.remoteId = remoteId;
        this.code = code;
        this.shared = shared;
        this.synced = synced;
        this.remoteStatus = remoteStatus;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public String getFacility() { return facility; }
    public String getPlannedDate() { return plannedDate; }
    public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public String getRemoteId() { return remoteId; }
    public String getCode() { return code; }
    public boolean isShared() { return shared; }
    public boolean isSynced() { return synced; }
    public String getRemoteStatus() { return remoteStatus; }
}
