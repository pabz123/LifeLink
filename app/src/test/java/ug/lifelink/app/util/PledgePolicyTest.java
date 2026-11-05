package ug.lifelink.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import ug.lifelink.app.model.Pledge;

public class PledgePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Test
    public void todayIsAllowed() {
        assertEquals(PledgePolicy.OK, PledgePolicy.checkDate(TODAY, TODAY));
    }

    @Test
    public void yesterdayIsRefused() {
        assertEquals(PledgePolicy.ERR_PAST, PledgePolicy.checkDate(TODAY.minusDays(1), TODAY));
    }

    @Test
    public void ninetyDaysAheadIsAllowedButNinetyOneIsNot() {
        assertEquals(PledgePolicy.OK, PledgePolicy.checkDate(TODAY.plusDays(90), TODAY));
        assertEquals(PledgePolicy.ERR_TOO_FAR, PledgePolicy.checkDate(TODAY.plusDays(91), TODAY));
    }

    @Test
    public void missingDateIsRefused() {
        assertEquals(PledgePolicy.ERR_NO_DATE, PledgePolicy.checkDate(null, TODAY));
    }

    @Test
    public void plannedPledgeWithPastDayShowsAsMissed() {
        assertEquals(Pledge.STATUS_MISSED,
                PledgePolicy.effectiveStatus(Pledge.STATUS_PLANNED, TODAY.minusDays(1), TODAY));
    }

    @Test
    public void plannedPledgeForTodayStaysPlanned() {
        assertEquals(Pledge.STATUS_PLANNED,
                PledgePolicy.effectiveStatus(Pledge.STATUS_PLANNED, TODAY, TODAY));
    }

    @Test
    public void completedPledgeIsNeverShownAsMissed() {
        assertEquals(Pledge.STATUS_COMPLETED,
                PledgePolicy.effectiveStatus(Pledge.STATUS_COMPLETED, TODAY.minusDays(5), TODAY));
    }

    @Test
    public void reminderFiresAtEightOnThePlannedDay() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 20, 0);
        long delay = PledgePolicy.reminderDelayMillis(LocalDate.of(2026, 11, 4), now);
        assertEquals(12L * 60 * 60 * 1000, delay); // 20:00 to 08:00 next day
    }

    @Test
    public void noReminderWhenEightOClockHasPassed() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 9, 0);
        assertEquals(-1L, PledgePolicy.reminderDelayMillis(LocalDate.of(2026, 11, 3), now));
    }

    @Test
    public void reminderIsStillSetBeforeEightOnTheSameDay() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 6, 30);
        assertTrue(PledgePolicy.reminderDelayMillis(LocalDate.of(2026, 11, 3), now) > 0);
    }
}
