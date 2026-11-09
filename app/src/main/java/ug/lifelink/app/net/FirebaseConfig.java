package ug.lifelink.app.net;

/**
 * Where the shared database and Firebase sign-in live. The web API key is the one already in
 * google-services.json. It identifies the project and is not a password: what a phone may read
 * or write is decided by the database rules, not by hiding this key.
 */
public final class FirebaseConfig {

    public static final String DATABASE_URL =
            "https://lifelink1234567890-default-rtdb.europe-west1.firebasedatabase.app";
    public static final String WEB_API_KEY = "AIzaSyDL_4nxw_RnqkGa2HQmjs8C7Af95pIUAO4";

    private FirebaseConfig() {
    }
}
