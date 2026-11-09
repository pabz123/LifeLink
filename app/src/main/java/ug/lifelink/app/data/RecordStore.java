package ug.lifelink.app.data;

import java.util.List;

import ug.lifelink.app.model.BloodRecord;

/**
 * Where records are kept. RecordRepository decides what is allowed (validation); a RecordStore
 * only stores and retrieves. There are two: SqliteRecordStore (the real database, Milestone 3)
 * and InMemoryRecordStore (a plain list, used by the unit tests so they run on a laptop).
 *
 * Authorisation is part of the contract: EVERY method takes the signed-in user's id and may
 * only see that user's rows. A record id that belongs to someone else behaves as "not found".
 *
 * Pure Java (no Android classes).
 */
public interface RecordStore {

    /** This user's records, newest first (equal dates: newest id first). */
    List<BloodRecord> listFor(long userId);

    /** The record, only if it belongs to this user; otherwise null. */
    BloodRecord find(long userId, long recordId);

    /** Stores a new record for this user and returns it with its new id. */
    BloodRecord insert(long userId, String kind, String bloodGroup, int units, String facility,
                       String status, String note, long createdAt);

    /**
     * Replaces the editable fields of this user's record. The id, owner and kind never change.
     *
     * @return false when this user has no such record
     */
    boolean update(long userId, long recordId, String bloodGroup, int units, String facility,
                   String status, String note, long createdAt);

    /** @return false when this user has no such record */
    boolean delete(long userId, long recordId);

    /**
     * Gives the user the sample records, once, ever. Returns true only the first time. The
     * "already seeded" fact is stored, so deleting every record does not bring the samples back.
     */
    boolean seedOnce(long userId, List<BloodRecord> sample);
}
