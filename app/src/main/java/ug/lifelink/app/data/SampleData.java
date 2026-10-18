package ug.lifelink.app.data;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.User;
import ug.lifelink.app.util.BloodGroups;

/**
 * Hard-coded sample data for the early milestones. Milestone 2 replaces the record list with
 * a collection the user can change, Milestone 3 with the database, and Milestone 4 replaces
 * the urgent requests with the REST API. Facility names are the regional blood banks and
 * hospitals named in the project proposal.
 */
public final class SampleData {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private SampleData() {
    }

    public static List<BloodRecord> forRole(String role, String bloodGroup, long userId, long now) {
        List<BloodRecord> list = new ArrayList<>();
        if (User.ROLE_HOSPITAL.equals(role)) {
            list.add(new BloodRecord(1, userId, BloodRecord.KIND_REQUEST, "B+", 4,
                    "Mbale Regional Referral Hospital", BloodRecord.STATUS_URGENT,
                    "Maternal emergency", now - DAY_MS));
            list.add(new BloodRecord(2, userId, BloodRecord.KIND_REQUEST, "O+", 2,
                    "Bududa Health Centre", BloodRecord.STATUS_OPEN,
                    "Road accident", now - 3 * DAY_MS));
            list.add(new BloodRecord(3, userId, BloodRecord.KIND_REQUEST, "A-", 3,
                    "Kitovu Hospital", BloodRecord.STATUS_FULFILLED,
                    "Scheduled surgery", now - 9 * DAY_MS));
        } else if (BloodGroups.isKnown(bloodGroup)) {
            list.add(new BloodRecord(1, userId, BloodRecord.KIND_DONATION, bloodGroup, 1,
                    "Nakasero Blood Bank", BloodRecord.STATUS_COMPLETED,
                    "", now - 92 * DAY_MS));
            list.add(new BloodRecord(2, userId, BloodRecord.KIND_DONATION, bloodGroup, 1,
                    "Mbarara Regional Blood Bank", BloodRecord.STATUS_COMPLETED,
                    "", now - 280 * DAY_MS));
            list.add(new BloodRecord(3, userId, BloodRecord.KIND_DONATION, bloodGroup, 1,
                    "Mbale Regional Blood Bank", BloodRecord.STATUS_COMPLETED,
                    "", now - 470 * DAY_MS));
        }
        // A donor who does not know their blood group has not donated yet: empty list.
        return list;
    }

    public static List<UrgentRequest> urgentRequests() {
        List<UrgentRequest> list = new ArrayList<>();
        list.add(new UrgentRequest(1, "Mbale Regional Referral Hospital", "B+", 4, 3.2,
                BloodRecord.STATUS_URGENT, "Maternal emergency", "+256700000001"));
        list.add(new UrgentRequest(2, "Mbarara Regional Referral Hospital", "A+", 2, 6.1,
                BloodRecord.STATUS_OPEN, "Scheduled surgery", "+256700000002"));
        list.add(new UrgentRequest(3, "Kitovu Hospital", "O+", 3, 8.5,
                BloodRecord.STATUS_URGENT, "Road accident", "+256700000003"));
        list.add(new UrgentRequest(4, "Bududa Health Centre", "A-", 3, 14.0,
                BloodRecord.STATUS_OPEN, "Child with severe anaemia", "+256700000004"));
        list.add(new UrgentRequest(5, "Nakasero Hospital", "AB-", 2, 21.4,
                BloodRecord.STATUS_OPEN, "Planned transfusion", "+256700000005"));
        return list;
    }
}
