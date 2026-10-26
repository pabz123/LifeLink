package ug.lifelink.app.alert;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.data.AlertLog;
import ug.lifelink.app.data.RequestCache;
import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.location.LocationStore;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.User;
import ug.lifelink.app.net.RequestFeedClient;
import ug.lifelink.app.net.RequestFeedParser;
import ug.lifelink.app.util.BloodGroups;
import ug.lifelink.app.util.RequestMatcher;

/**
 * One alert check: refresh the saved requests (when asked to and the network allows), match
 * them to the donor, and notify about the ones that pass AlertRules. Used by the background
 * job and by Demo mode's "Run alert check now" button. Works on the phone's saved copy, so an
 * offline phone still alerts about requests it downloaded earlier.
 *
 * Runs on a worker thread only (database and network).
 */
public final class RequestChecker {

    private static final String TAG = "RequestChecker";

    private RequestChecker() {
    }

    /**
     * @param fetch true to download the feed first; false to use only the saved copy
     * @return how many notifications were posted
     */
    public static int run(Context context, long userId, boolean fetch) {
        Context app = context.getApplicationContext();
        User user = new UserRepository(app).findById(userId);
        if (user == null || user.isHospital() || !BloodGroups.isKnown(user.getBloodGroup())) {
            return 0;
        }

        RequestCache cache = new RequestCache(app);
        if (fetch) {
            try {
                RequestFeedParser.Result result = RequestFeedClient.fetch();
                cache.replaceAll(result.requests, System.currentTimeMillis());
            } catch (java.io.IOException | org.json.JSONException e) {
                Log.w(TAG, "Feed not reachable, checking the saved copy", e);
            }
        }

        double[] where = new LocationStore(app).effective();
        if (where == null) {
            return 0; // no position known: "nearby" cannot be decided
        }
        long now = System.currentTimeMillis();
        List<UrgentRequest> matched = RequestMatcher.match(user.getBloodGroup(), true,
                where[0], where[1], now, cache.listAll());

        AlertLog log = new AlertLog(app);
        List<UrgentRequest> fresh = AlertRules.select(matched, log.alertedIds(userId));
        List<String> done = new ArrayList<>();
        for (UrgentRequest r : fresh) {
            if (AlertNotifier.notify(app, r)) {
                done.add(r.getRemoteId());
            }
        }
        if (!done.isEmpty()) {
            log.markAlerted(userId, done, now);
        }
        return done.size();
    }
}
