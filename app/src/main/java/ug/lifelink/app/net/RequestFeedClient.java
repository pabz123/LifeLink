package ug.lifelink.app.net;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads the shared list of blood requests: one HTTPS GET to the Firebase Realtime Database
 * REST address, no library needed.
 *
 *   GET https://<database>.firebasedatabase.app/requests.json   (answers JSON)
 *
 * The database rules let anyone read the "requests" list and let nobody write to it from the
 * app, so no key or password is sent. Only https is used (cleartext traffic is never allowed),
 * both timeouts are short so a bad connection fails quickly, and the answer is capped at 512 KB.
 *
 * This touches the network, so callers must run it OFF the main thread.
 */
public final class RequestFeedClient {

    public static final String FEED_URL =
            "https://lifelink1234567890-default-rtdb.europe-west1.firebasedatabase.app/requests.json";

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 512 * 1024;

    private RequestFeedClient() {
    }

    /**
     * @throws IOException when the phone is offline, the server answers with an error, the answer
     *                     is too large, or the text is not valid JSON
     */
    public static RequestFeedParser.Result fetch() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("The request list answered with HTTP " + code);
            }
            String body = readLimited(connection.getInputStream());
            try {
                return RequestFeedParser.parse(body);
            } catch (JSONException e) {
                throw new IOException("The request list was not valid JSON", e);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BYTES) {
                    throw new IOException("The request list is too large");
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
