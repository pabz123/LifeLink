package ug.lifelink.app.pledge;

import java.io.IOException;
import java.net.URLEncoder;

import ug.lifelink.app.net.FirebaseConfig;
import ug.lifelink.app.net.PledgeJson;
import ug.lifelink.app.net.RestHttp;

/**
 * The few requests the app makes about a pledge in the shared database
 * ({@code /pledges/<facility>/<pledge>}). The Firebase REST interface answers 200 on success and
 * 401 when the database rules refuse the request.
 */
public final class PledgeRemote {

    private PledgeRemote() {
    }

    private static String url(String facilityId, String remoteId, String child, String idToken) {
        try {
            return FirebaseConfig.DATABASE_URL + "/pledges/" + URLEncoder.encode(facilityId, "UTF-8")
                    + "/" + URLEncoder.encode(remoteId, "UTF-8") + (child == null ? "" : "/" + child)
                    + ".json?auth=" + URLEncoder.encode(idToken, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates the shared copy. @return the HTTP status */
    public static int create(String idToken, String facilityId, String remoteId, String recordJson)
            throws IOException {
        return RestHttp.send("PUT", url(facilityId, remoteId, null, idToken), recordJson,
                "application/json").code;
    }

    /** Sets the status of the shared copy (the donor may only cancel). @return the HTTP status */
    public static int setStatus(String idToken, String facilityId, String remoteId, String status)
            throws IOException {
        return RestHttp.send("PUT", url(facilityId, remoteId, "status", idToken),
                PledgeJson.quote(status), "application/json").code;
    }

    /** Removes the shared copy (the donor withdraws it). @return the HTTP status */
    public static int delete(String idToken, String facilityId, String remoteId) throws IOException {
        return RestHttp.send("DELETE", url(facilityId, remoteId, null, idToken), null, null).code;
    }

    /** @return the status the hospital set, or null when there is none or the request failed */
    public static String readStatus(String idToken, String facilityId, String remoteId)
            throws IOException {
        RestHttp.Response r = RestHttp.send("GET", url(facilityId, remoteId, "status", idToken), null, null);
        return r.ok() ? PledgeJson.parseStatus(r.body) : null;
    }
}
