package ug.lifelink.app.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.Urgency;

/**
 * Decides which of the already matched requests deserve a notification. A request is notified
 * only when ALL of these hold:
 *   - its hospital is verified (an unverified post never wakes a donor's phone),
 *   - it is Critical or Urgent (Routine requests appear in the list but stay silent),
 *   - the donor's distance to it is known (a "nearby" alert needs a position),
 *   - this donor has not been notified about it before.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class AlertRules {

    private AlertRules() {
    }

    /**
     * @param matched output of RequestMatcher (compatible, unexpired, inside the radius)
     * @param alreadyAlerted remote ids this donor was notified about
     */
    public static List<UrgentRequest> select(List<UrgentRequest> matched, Set<String> alreadyAlerted) {
        List<UrgentRequest> out = new ArrayList<>();
        for (UrgentRequest r : matched) {
            boolean loud = Urgency.CRITICAL.equals(r.getUrgency()) || Urgency.URGENT.equals(r.getUrgency());
            if (r.isVerified() && loud && r.hasDistance() && !alreadyAlerted.contains(r.getRemoteId())) {
                out.add(r);
            }
        }
        return out;
    }
}
