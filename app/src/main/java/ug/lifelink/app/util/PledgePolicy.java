package ug.lifelink.app.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import ug.lifelink.app.model.Pledge;

/** Rules for pledges. Plain Java with no Android classes, so it is unit tested on a laptop. */
public final class PledgePolicy {

    /** A pledge may be made for today or up to this many days ahead. */
    public static final int MAX_DAYS_AHEAD = 90;
    /** The reminder is shown at this hour on the planned day. */
    public static final int REMINDER_HOUR = 8;

    public static final int OK = 0;
    public static final int ERR_NO_DATE = 1;
    public static final int ERR_PAST = 2;
    public static final int ERR_TOO_FAR = 3;

    private PledgePolicy() {
    }

    /** @return OK or one of the ERR_ codes */
    public static int checkDate(LocalDate planned, LocalDate today) {
        if (planned == null) {
            return ERR_NO_DATE;
        }
        if (planned.isBefore(today)) {
            return ERR_PAST;
        }
        if (planned.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            return ERR_TOO_FAR;
        }
        return OK;
    }

    /** A planned pledge whose day has passed is shown as missed; nothing is changed in storage. */
    public static String effectiveStatus(String status, LocalDate planned, LocalDate today) {
        if (Pledge.STATUS_PLANNED.equals(status) && planned != null && planned.isBefore(today)) {
            return Pledge.STATUS_MISSED;
        }
        return status;
    }

    /**
     * Milliseconds from now until the reminder (08:00 on the planned day), or -1 when there
     * should be no reminder because that moment has already passed.
     */
    public static long reminderDelayMillis(LocalDate planned, LocalDateTime now) {
        if (planned == null) {
            return -1;
        }
        long delay = Duration.between(now, planned.atTime(REMINDER_HOUR, 0)).toMillis();
        return delay < 0 ? -1 : delay;
    }
}
