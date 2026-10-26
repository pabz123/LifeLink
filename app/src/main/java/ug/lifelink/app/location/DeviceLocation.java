package ug.lifelink.app.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.location.Location;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Asks the phone where it is, using Google Play services' fused provider, which combines Wi-Fi,
 * mobile network and GPS and is quick and kind to the battery.
 *
 * Only the COARSE location permission is requested: roughly a few hundred metres is enough to
 * tell whether a hospital is within 15, 30 or 50 km, and it is less intrusive for the user.
 *
 * The answer arrives later, on the main thread. A null answer means the position could not be
 * found (location switched off, no recent fix, or Play services missing); the app then carries
 * on without distances.
 */
public final class DeviceLocation {

    /** Receives the position, or null when it could not be found. */
    public interface Callback {
        void onLocation(Location location);
    }

    private DeviceLocation() {
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // checked just below; denied permission returns null
    public static void get(Context context, final Callback callback) {
        if (!hasPermission(context)) {
            callback.onLocation(null);
            return;
        }
        try {
            final FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(context);
            client.getLastLocation().addOnSuccessListener(last -> {
                if (last != null) {
                    callback.onLocation(last);
                    return;
                }
                // No recent position stored: ask for a fresh one.
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener(callback::onLocation)
                        .addOnFailureListener(e -> callback.onLocation(null));
            }).addOnFailureListener(e -> callback.onLocation(null));
        } catch (RuntimeException e) {
            callback.onLocation(null);
        }
    }
}
