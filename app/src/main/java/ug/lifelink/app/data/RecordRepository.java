package ug.lifelink.app.data;

import java.util.List;

import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.util.Validators;

/**
 * The rules for records. This class decides what may be saved (validation) and hands the
 * actual storing to a RecordStore: SQLite in the app (Milestone 3), a plain list in unit tests.
 * Milestone 2 kept the records in memory; the screens did not change, because the method
 * names here stayed the same.
 *
 * Authorisation is built into the shape of the API: every method takes the signed-in user's
 * id and the store only ever looks at that user's own rows. A record id that belongs to
 * someone else is simply "not found". Nothing here can return another user's row.
 *
 * With the SQLite store these methods touch the disk, so callers must run them OFF the main
 * thread.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class RecordRepository {

    private final RecordStore store;

    public RecordRepository(RecordStore store) {
        this.store = store;
    }

    /**
     * Gives a new user the sample records once, ever. If the user later deletes them all, they
     * are not put back, because the store remembers that this user was already seeded.
     */
    public void seedOnce(long userId, List<BloodRecord> sample) {
        store.seedOnce(userId, sample);
    }

    /** This user's records, newest first. Changing the returned list changes nothing stored. */
    public List<BloodRecord> listFor(long userId) {
        return store.listFor(userId);
    }

    /** Returns the record only if it belongs to this user, otherwise null. */
    public BloodRecord get(long userId, long recordId) {
        return store.find(userId, recordId);
    }

    /**
     * Creates a record for this user.
     *
     * @throws IllegalArgumentException when any value breaks the rules in Validators. The
     *                                  screens already check these, so this only fires if a
     *                                  screen is bypassed or has a bug.
     */
    public BloodRecord add(long userId, String kind, String bloodGroup, int units,
                           String facility, String status, String note,
                           long createdAt, long nowMillis) {
        check(kind, bloodGroup, units, facility, status, note, createdAt, nowMillis);
        return store.insert(userId, kind, bloodGroup, units, facility.trim(), status,
                note == null ? "" : note.trim(), createdAt);
    }

    /**
     * Replaces the editable fields of one of this user's records. The id, owner and kind never
     * change. Returns false when the user has no such record.
     *
     * @throws IllegalArgumentException when any value breaks the rules in Validators
     */
    public boolean update(long userId, long recordId, String bloodGroup, int units,
                          String facility, String status, String note,
                          long createdAt, long nowMillis) {
        BloodRecord old = store.find(userId, recordId);
        if (old == null) {
            return false;
        }
        check(old.getKind(), bloodGroup, units, facility, status, note, createdAt, nowMillis);
        return store.update(userId, recordId, bloodGroup, units, facility.trim(), status,
                note == null ? "" : note.trim(), createdAt);
    }

    /** Removes one of this user's records. Returns false when the user has no such record. */
    public boolean delete(long userId, long recordId) {
        return store.delete(userId, recordId);
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
