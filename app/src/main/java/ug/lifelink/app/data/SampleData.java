package ug.lifelink.app.data;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.model.User;

/**
 * Hard-coded sample records for Milestone 1 only. Milestone 2 replaces this with a
 * collection the user can change, and Milestone 3 replaces it with the database.
 * Facility names are the regional blood banks named in the project proposal.
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
        } else {
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
        return list;
    }
}
