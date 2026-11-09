package ug.lifelink.app.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * One small HTTPS request helper for the Firebase REST calls. https only, short timeouts, and the
 * answer is capped at 64 KB. A network failure throws IOException; any HTTP answer, including an
 * error such as 401, is returned so the caller can decide what it means.
 * Callers must run it OFF the main thread.
 */
public final class RestHttp {

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 64 * 1024;

    public static final class Response {
        public final int code;
        public final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    private RestHttp() {
    }

    public static Response send(String method, String url, String body, String contentType)
            throws IOException {
        URL target = new URL(url);
        if (!"https".equals(target.getProtocol())) {
            throw new IOException("https only");
        }
        HttpURLConnection c = (HttpURLConnection) target.openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(code, in == null ? "" : read(in));
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) {
                    throw new IOException("answer too large");
                }
            }
            return out.toString("UTF-8");
        }
    }
}
