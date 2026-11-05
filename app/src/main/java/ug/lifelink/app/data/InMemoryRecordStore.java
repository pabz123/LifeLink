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

/**
 * A RecordStore that keeps everything in memory. The app itself uses SqliteRecordStore; this
 * one exists so the repository rules can be unit tested without a phone or a database.
 *
 * Pure Java (no Android classes).
 */
public final class InMemoryRecordStore implements RecordStore {

    private final Map<Long, List<BloodRecord>> recordsByUser = new HashMap<>();
    private final Set<Long> seededUsers = new HashSet<>();
    private long nextId = 1000L;

    private static final Comparator<BloodRecord> NEWEST_FIRST = (a, b) -> {
        int byDate = Long.compare(b.getCreatedAt(), a.getCreatedAt());
        return byDate != 0 ? byDate : Long.compare(b.getId(), a.getId());
    };

    @Override
    public synchronized List<BloodRecord> listFor(long userId) {
        List<BloodRecord> copy = new ArrayList<>(listOf(userId));
        Collections.sort(copy, NEWEST_FIRST);
        return copy;
    }

    @Override
    public synchronized BloodRecord find(long userId, long recordId) {
        for (BloodRecord record : listOf(userId)) {
            if (record.getId() == recordId) {
                return record;
            }
        }
        return null;
    }

    @Override
    public synchronized BloodRecord insert(long userId, String kind, String bloodGroup, int units,
                                           String facility, String status, String note,
                                           long createdAt) {
        BloodRecord record = new BloodRecord(nextId++, userId, kind, bloodGroup, units,
                facility, status, note, createdAt);
        listOf(userId).add(record);
        return record;
    }

    @Override
    public synchronized boolean update(long userId, long recordId, String bloodGroup, int units,
                                       String facility, String status, String note,
                                       long createdAt) {
        List<BloodRecord> list = listOf(userId);
        for (int i = 0; i < list.size(); i++) {
            BloodRecord old = list.get(i);
            if (old.getId() == recordId) {
                list.set(i, new BloodRecord(old.getId(), userId, old.getKind(), bloodGroup, units,
                        facility, status, note, createdAt));
                return true;
            }
        }
        return false;
    }

    @Override
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

    @Override
    public synchronized boolean seedOnce(long userId, List<BloodRecord> sample) {
        if (!seededUsers.add(userId)) {
            return false;
        }
        for (BloodRecord s : sample) {
            insert(userId, s.getKind(), s.getBloodGroup(), s.getUnits(), s.getFacility(),
                    s.getStatus(), s.getNote(), s.getCreatedAt());
        }
        return true;
    }

    private List<BloodRecord> listOf(long userId) {
        List<BloodRecord> list = recordsByUser.get(userId);
        if (list == null) {
            list = new ArrayList<>();
            recordsByUser.put(userId, list);
        }
        return list;
    }
}
