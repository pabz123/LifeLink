package ug.lifelink.app.model;

/**
 * An urgent hospital request for blood. In Milestone 1 these are sample objects; in
 * Milestone 4 the same fields are filled from the REST API's JSON response, and the
 * distance is computed from the phone's location.
 */
public class UrgentRequest {

    private final long id;
    private final String facility;
    private final String bloodGroup;
    private final int units;
    private final double distanceKm;
    private final String status; // BloodRecord.STATUS_URGENT or STATUS_OPEN
    private final String note;
    private final String contactPhone;

    public UrgentRequest(long id, String facility, String bloodGroup, int units,
                         double distanceKm, String status, String note, String contactPhone) {
        this.id = id;
        this.facility = facility;
        this.bloodGroup = bloodGroup;
        this.units = units;
        this.distanceKm = distanceKm;
        this.status = status;
        this.note = note;
        this.contactPhone = contactPhone;
    }

    public long getId() {
        return id;
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

    public double getDistanceKm() {
        return distanceKm;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    /** Number the donor can dial. Sample requests use placeholder numbers; the API supplies real ones in M4. */
    public String getContactPhone() {
        return contactPhone;
    }
}
