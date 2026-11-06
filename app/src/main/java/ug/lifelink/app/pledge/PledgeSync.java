package ug.lifelink.app.pledge;

import android.content.Context;

import java.io.IOException;
import java.util.List;

import ug.lifelink.app.data.Facilities;
import ug.lifelink.app.data.Pledges;
import ug.lifelink.app.model.Pledge;
import ug.lifelink.app.net.AuthSession;
import ug.lifelink.app.net.PledgeJson;

/**
 * Keeps the shared copy of the donor's shared pledges up to date, and reads back what the
 * hospital answered. Only pledges the donor agreed to share are ever sent. Callers run it OFF the
 * main thread; a network failure throws IOException so the caller can try again later.
 */
public final class PledgeSync {

    private PledgeSync() {
    }

    /** Sends every shared pledge that is out of date. @return how many were sent */
    public static int upload(Context context, long userId) throws IOException {
        Pledges pledges = new Pledges(context);
        List<Pledge> pending = pledges.pendingSync(userId);
        if (pending.isEmpty()) {
            return 0;
        }
        AuthSession session = FirebaseSignIn.ensure(context);
        int sent = 0;
        for (Pledge p : pending) {
            String facilityId = Facilities.idFor(p.getFacility());
            int code;
            if (Pledge.STATUS_CANCELLED.equals(p.getStatus())) {
                code = PledgeRemote.setStatus(session.idToken, facilityId, p.getRemoteId(),
                        Pledge.STATUS_CANCELLED);
            } else {
                code = PledgeRemote.create(session.idToken, facilityId, p.getRemoteId(),
                        PledgeJson.build(session.uid, p.getCode(), p.getPlannedDate(), p.getCreatedAt()));
            }
            // 200: done. 401: the rules refuse it (for example the hospital already confirmed
            // this pledge, or it was sent before), so trying again would never succeed.
            if (code == 200 || code == 401) {
                pledges.markSynced(userId, p.getId());
                sent++;
            } else {
                throw new IOException("unexpected answer " + code);
            }
        }
        return sent;
    }

    /** Asks for the hospital's answer to each shared pledge. @return how many answers changed */
    public static int refreshAnswers(Context context, long userId) throws IOException {
        Pledges pledges = new Pledges(context);
        List<Pledge> shared = pledges.sharedForStatusCheck(userId);
        if (shared.isEmpty()) {
            return 0;
        }
        AuthSession session = FirebaseSignIn.ensure(context);
        int changed = 0;
        for (Pledge p : shared) {
            String answer = PledgeRemote.readStatus(session.idToken,
                    Facilities.idFor(p.getFacility()), p.getRemoteId());
            // Only answers the hospital gives matter here; the donor's own cancel is already known.
            if (answer != null && !answer.equals(p.getRemoteStatus())) {
                pledges.setRemoteStatus(userId, p.getId(), answer);
                changed++;
            }
        }
        return changed;
    }

    /** Removes the shared copy of a pledge the donor deleted. @return true when it is gone */
    public static boolean withdraw(Context context, String facility, String remoteId) throws IOException {
        AuthSession session = FirebaseSignIn.ensure(context);
        int code = PledgeRemote.delete(session.idToken, Facilities.idFor(facility), remoteId);
        return code == 200 || code == 401;
    }
}
