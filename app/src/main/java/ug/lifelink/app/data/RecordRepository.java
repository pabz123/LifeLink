package ug.lifelink.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.util.Validators;

/**
 * Milestone 2 record store: the records live in memory, in a collection the app changes from
 * code (add, edit, delete). Milestone 3 replaces the inside of this class with SQLite and
 * keeps the same method names, so the screens do not change.
 *
 * Authorisation is built into the shape of the API: every method takes the signed-in user's
 * id and only ever looks inside that user's own list. A record id that belongs to someone
 * else is simply "not found". Nothing here can return another user's row.
 *
 * Records live as long as the app process. They are lost when Android ends the process;
 * surviving that is exactly what Milestone 3's database adds.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class RecordRepository {

    private static final RecordRepository INSTANCE = new RecordRepository();

    private final Map<Long, List<BloodRecord>> recordsByUser = new HashMap<>();
    private final Set<Long> seededUsers = new HashSet<>();
    private long nextId = 1000L;

    /** Newest record first; equal dates fall back to the newest id. */
    private static final Comparator<BloodRecord> NEWEST_FIRST = (a, b) -> {
        int byDate = Long.compare(b.getCreatedAt(), a.getCreatedAt());
        return byDate != 0 ? byDate : Long.compare(b.getId(), a.getId());
    };

    public static RecordRepository getInstance() {
        return INSTANCE;
    }

    /** Package-visible so tests can build an isolated repository. */
    RecordRepository() {
    }

    /**
     * Gives a new user the sample records once. If the user later deletes them all, they are
     * not put back, because the user is recorded as already seeded.
     */
    public synchronized void seedOnce(long userId, List<BloodRecord> sample) {
        if (seededUsers.add(userId)) {
            listOf(userId).addAll(sample);
        }
    }

    /** A copy of this user's records, newest first. Changing the copy changes nothing stored. */
    public synchronized List<BloodRecord> listFor(long userId) {
        List<BloodRecord> copy = new ArrayList<>(listOf(userId));
        Collections.sort(copy, NEWEST_FIRST);
        return copy;
    }

    /** Returns the record only if it belongs to this user, otherwise null. */
    public synchronized BloodRecord get(long userId, long recordId) {
        for (BloodRecord record : listOf(userId)) {
            if (record.getId() == recordId) {
                return record;
            }
        }
        return null;
    }

    /**
     * Creates a record for this user.
     *
     * @throws IllegalArgumentException when any value breaks the rules in Validators. The
     *                                  screens already check these, so this only fires if a
     *                                  screen is bypassed or has a bug.
     */
    public synchronized BloodRecord add(long userId, String kind, String bloodGroup, int units,
                                        String facility, String status, String note,
                                        long createdAt, long nowMillis) {
        check(kind, bloodGroup, units, facility, status, note, createdAt, nowMillis);
        BloodRecord record = new BloodRecord(nextId++, userId, kind, bloodGroup, units,
                facility.trim(), status, note == null ? "" : note.trim(), createdAt);
        listOf(userId).add(record);
        return record;
    }

    /**
     * Replaces the editable fields of one of this user's records. The id, owner and kind never
     * change. Returns false when the user has no such record.
     */
    public synchronized boolean update(long userId, long recordId, String bloodGroup, int units,
                                       String facility, String status, String note,
                                       long createdAt, long nowMillis) {
        List<BloodRecord> list = listOf(userId);
        for (int i = 0; i < list.size(); i++) {
            BloodRecord old = list.get(i);
            if (old.getId() == recordId) {
                check(old.getKind(), bloodGroup, units, facility, status, note, createdAt, nowMillis);
                list.set(i, new BloodRecord(old.getId(), userId, old.getKind(), bloodGroup, units,
                        facility.trim(), status, note == null ? "" : note.trim(), createdAt));
                return true;
            }
        }
        return false;
    }

    /** Removes one of this user's records. Returns false when the user has no such record. */
    public synchronized boolean delete(long userId, long recordId) {
        List<BloodRecord> list = listOf(userId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == recordId) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    private List<BloodRecord> listOf(long userId) {
        List<BloodRecord> list = recordsByUser.get(userId);
        if (list == null) {
            list = new ArrayList<>();
            recordsByUser.put(userId, list);
        }
        return list;
    }

    private static void check(String kind, String bloodGroup, int units, String facility,
                              String status, String note, long createdAt, long nowMillis) {
        if (!BloodRecord.KIND_DONATION.equals(kind) && !BloodRecord.KIND_REQUEST.equals(kind)) {
            throw new IllegalArgumentException("Unknown record kind");
        }
        if (Validators.recordBloodGroupError(bloodGroup) != 0) {
            throw new IllegalArgumentException("Invalid blood group");
        }
        if (units < Validators.UNITS_MIN || units > Validators.UNITS_MAX) {
            throw new IllegalArgumentException("Units out of range");
        }
        if (Validators.facilityError(facility) != 0) {
            throw new IllegalArgumentException("Invalid facility");
        }
        if (!Validators.isValidRecordStatus(kind, status)) {
            throw new IllegalArgumentException("Invalid status for this kind of record");
        }
        if (Validators.noteError(note) != 0) {
            throw new IllegalArgumentException("Note too long");
        }
        if (Validators.dateError(createdAt, nowMillis) != 0) {
            throw new IllegalArgumentException("Date is in the future");
        }
    }
}
