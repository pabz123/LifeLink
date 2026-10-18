package ug.lifelink.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.model.BloodRecord;

/**
 * Tests for the in-memory record store. The most important ones check ownership: one user can
 * never see, change or delete another user's records.
 */
public class RecordRepositoryTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_000 * DAY;
    private static final long ALICE = 1;
    private static final long BOB = 2;

    private BloodRecord addDonation(RecordRepository repo, long user, String facility, long date) {
        return repo.add(user, BloodRecord.KIND_DONATION, "O+", 1, facility,
                BloodRecord.STATUS_COMPLETED, "", date, NOW);
    }

    @Test
    public void add_thenList_returnsTheRecord() {
        RecordRepository repo = new RecordRepository();
        addDonation(repo, ALICE, "Nakasero Blood Bank", NOW - DAY);
        List<BloodRecord> list = repo.listFor(ALICE);
        assertEquals(1, list.size());
        assertEquals("Nakasero Blood Bank", list.get(0).getFacility());
        assertEquals(ALICE, list.get(0).getUserId());
    }

    @Test
    public void list_isNewestFirst() {
        RecordRepository repo = new RecordRepository();
        addDonation(repo, ALICE, "Old", NOW - 50 * DAY);
        addDonation(repo, ALICE, "New", NOW - 1 * DAY);
        addDonation(repo, ALICE, "Middle", NOW - 20 * DAY);
        List<BloodRecord> list = repo.listFor(ALICE);
        assertEquals("New", list.get(0).getFacility());
        assertEquals("Middle", list.get(1).getFacility());
        assertEquals("Old", list.get(2).getFacility());
    }

    @Test
    public void changingTheReturnedList_doesNotChangeTheStore() {
        RecordRepository repo = new RecordRepository();
        addDonation(repo, ALICE, "Kitovu Hospital", NOW);
        repo.listFor(ALICE).clear();
        assertEquals(1, repo.listFor(ALICE).size());
    }

    @Test
    public void usersNeverSeeEachOthersRecords() {
        RecordRepository repo = new RecordRepository();
        addDonation(repo, ALICE, "Alice's donation", NOW);
        assertEquals(0, repo.listFor(BOB).size());
    }

    @Test
    public void get_withAnotherUsersId_findsNothing() {
        RecordRepository repo = new RecordRepository();
        BloodRecord alices = addDonation(repo, ALICE, "Private", NOW);
        assertTrue(repo.get(ALICE, alices.getId()) != null);
        assertEquals(null, repo.get(BOB, alices.getId()));
    }

    @Test
    public void delete_withAnotherUsersId_changesNothing() {
        RecordRepository repo = new RecordRepository();
        BloodRecord alices = addDonation(repo, ALICE, "Private", NOW);
        assertFalse(repo.delete(BOB, alices.getId()));
        assertEquals(1, repo.listFor(ALICE).size());
    }

    @Test
    public void update_withAnotherUsersId_changesNothing() {
        RecordRepository repo = new RecordRepository();
        BloodRecord alices = addDonation(repo, ALICE, "Private", NOW);
        assertFalse(repo.update(BOB, alices.getId(), "A+", 2, "Hacked", BloodRecord.STATUS_COMPLETED,
                "", NOW, NOW));
        assertEquals("Private", repo.get(ALICE, alices.getId()).getFacility());
    }

    @Test
    public void update_changesFields_butKeepsIdOwnerAndKind() {
        RecordRepository repo = new RecordRepository();
        BloodRecord original = addDonation(repo, ALICE, "Before", NOW - DAY);
        assertTrue(repo.update(ALICE, original.getId(), "B+", 3, "  After  ",
                BloodRecord.STATUS_COMPLETED, "  note ", NOW - 2 * DAY, NOW));
        BloodRecord changed = repo.get(ALICE, original.getId());
        assertEquals("After", changed.getFacility());
        assertEquals("B+", changed.getBloodGroup());
        assertEquals(3, changed.getUnits());
        assertEquals("note", changed.getNote());
        assertEquals(original.getId(), changed.getId());
        assertEquals(ALICE, changed.getUserId());
        assertEquals(BloodRecord.KIND_DONATION, changed.getKind());
    }

    @Test
    public void delete_removesTheRecord() {
        RecordRepository repo = new RecordRepository();
        BloodRecord record = addDonation(repo, ALICE, "Gone soon", NOW);
        assertTrue(repo.delete(ALICE, record.getId()));
        assertEquals(0, repo.listFor(ALICE).size());
        assertFalse(repo.delete(ALICE, record.getId()));
    }

    @Test
    public void seedOnce_addsSamplesOnlyOnce_evenAfterEverythingIsDeleted() {
        RecordRepository repo = new RecordRepository();
        List<BloodRecord> sample = new ArrayList<>();
        sample.add(new BloodRecord(1, ALICE, BloodRecord.KIND_DONATION, "O+", 1, "Sample",
                BloodRecord.STATUS_COMPLETED, "", NOW - 30 * DAY));
        repo.seedOnce(ALICE, sample);
        repo.seedOnce(ALICE, sample);
        assertEquals(1, repo.listFor(ALICE).size());
        repo.delete(ALICE, 1);
        repo.seedOnce(ALICE, sample);
        assertEquals(0, repo.listFor(ALICE).size());
    }

    // ---- the repository re-checks the rules even if a screen is bypassed

    @Test(expected = IllegalArgumentException.class)
    public void add_withZeroUnits_isRejected() {
        new RecordRepository().add(ALICE, BloodRecord.KIND_DONATION, "O+", 0, "Somewhere",
                BloodRecord.STATUS_COMPLETED, "", NOW, NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_withUnknownBloodGroup_isRejected() {
        new RecordRepository().add(ALICE, BloodRecord.KIND_DONATION, "UNKNOWN", 1, "Somewhere",
                BloodRecord.STATUS_COMPLETED, "", NOW, NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_withEmptyFacility_isRejected() {
        new RecordRepository().add(ALICE, BloodRecord.KIND_DONATION, "O+", 1, "  ",
                BloodRecord.STATUS_COMPLETED, "", NOW, NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_donationWithRequestStatus_isRejected() {
        new RecordRepository().add(ALICE, BloodRecord.KIND_DONATION, "O+", 1, "Somewhere",
                BloodRecord.STATUS_URGENT, "", NOW, NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_dateInTheFuture_isRejected() {
        new RecordRepository().add(ALICE, BloodRecord.KIND_DONATION, "O+", 1, "Somewhere",
                BloodRecord.STATUS_COMPLETED, "", NOW + 30 * DAY, NOW);
    }

    @Test
    public void add_requestWithEachRequestStatus_isAccepted() {
        RecordRepository repo = new RecordRepository();
        for (String status : new String[]{BloodRecord.STATUS_OPEN, BloodRecord.STATUS_URGENT,
                BloodRecord.STATUS_FULFILLED}) {
            repo.add(BOB, BloodRecord.KIND_REQUEST, "B+", 2, "Mbale Hospital", status, "", NOW, NOW);
        }
        assertEquals(3, repo.listFor(BOB).size());
    }
}
