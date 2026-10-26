package ug.lifelink.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.util.SessionManager;
import ug.lifelink.app.alert.AlertNotifier;
import ug.lifelink.app.alert.RequestChecker;
import ug.lifelink.app.data.AlertLog;
import ug.lifelink.app.data.RequestCache;
import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.location.LocationStore;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.Urgency;
import ug.lifelink.app.model.User;

/**
 * Demo mode, for showing location and alerts in a classroom without travelling:
 *   1. pick a pretend place (the phone's real position is then ignored),
 *   2. post a test request about 3 km from it, for the signed-in donor's own blood group,
 *   3. run the alert check, which posts the same notification a real request would.
 * "Clear demo" puts everything back. Test requests are local to this phone (ids start with
 * "demo-"), never uploaded, and a real download never deletes them.
 */
public class DemoActivity extends AppCompatActivity {

    private static final String TAG = "DemoActivity";
    static final String EXTRA_USER_ID = "ug.lifelink.app.extra.USER_ID";

    private static final String[] PLACES = {"Mbarara", "Kabale", "Kampala", "Mbale"};
    private static final double[][] COORDS = {
            {-0.6072, 30.6545}, {-1.2490, 29.9899}, {0.3476, 32.5825}, {1.0775, 34.1753}};
    /** About 3 km north of the pretend position. */
    private static final double TEST_OFFSET_DEG = 0.027;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long userId;
    private Spinner spinPlace;
    private TextView tvStatus;
    private LocationStore locationStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = getIntent().getLongExtra(EXTRA_USER_ID, -1L);
        if (userId < 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_demo);
        locationStore = new LocationStore(this);
        spinPlace = findViewById(R.id.spinPlace);
        tvStatus = findViewById(R.id.tvDemoStatus);
        spinPlace.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PLACES));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSetPlace).setOnClickListener(v -> setPlace());
        findViewById(R.id.btnPostTest).setOnClickListener(v -> postTestRequest());
        findViewById(R.id.btnRunCheck).setOnClickListener(v -> runCheck());
        findViewById(R.id.btnClearDemo).setOnClickListener(v -> clearDemo());
        showState();
    }

    @Override
    protected void onStop() {
        // Keeps the idle timer fresh while the person works on this screen.
        new SessionManager(this).touch(userId);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void showState() {
        tvStatus.setText(locationStore.isDemoOn()
                ? getString(R.string.demo_state_on, locationStore.demoLabel())
                : getString(R.string.demo_state_off));
    }

    private void setPlace() {
        int i = spinPlace.getSelectedItemPosition();
        locationStore.setDemo(PLACES[i], COORDS[i][0], COORDS[i][1]);
        showState();
    }

    private void postTestRequest() {
        double[] where = locationStore.effective();
        if (where == null) {
            tvStatus.setText(R.string.demo_need_place);
            return;
        }
        final double lat = where[0] + TEST_OFFSET_DEG;
        final double lng = where[1];
        worker.execute(() -> {
            String message;
            try {
                User user = new UserRepository(this).findById(userId);
                if (user == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                UrgentRequest test = new UrgentRequest(RequestCache.DEMO_PREFIX + now,
                        getString(R.string.demo_facility), user.getBloodGroup(), 2,
                        Urgency.CRITICAL, getString(R.string.demo_note), "", lat, lng, true,
                        now, now + 60L * 60 * 1000, Double.NaN);
                new RequestCache(this).upsert(test, 0L);
                message = getString(R.string.demo_posted, user.getBloodGroup());
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not post the test request", e);
                message = getString(R.string.error_generic);
            }
            show(message);
        });
    }

    private void runCheck() {
        if (!AlertNotifier.canNotify(this)) {
            tvStatus.setText(R.string.demo_notifications_off);
            return;
        }
        worker.execute(() -> {
            String message;
            try {
                int sent = RequestChecker.run(this, userId, false);
                message = sent > 0
                        ? getResources().getQuantityString(R.plurals.demo_alerts_sent, sent, sent)
                        : getString(R.string.demo_alerts_none);
            } catch (RuntimeException e) {
                Log.e(TAG, "Alert check failed", e);
                message = getString(R.string.error_generic);
            }
            show(message);
        });
    }

    private void clearDemo() {
        locationStore.clearDemo();
        worker.execute(() -> {
            String message;
            try {
                new RequestCache(this).clearDemo();
                new AlertLog(this).clear(userId);
                message = getString(R.string.demo_cleared);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not clear demo data", e);
                message = getString(R.string.error_generic);
            }
            show(message);
        });
    }

    private void show(final String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                showState();
                tvStatus.append("\n" + message);
            }
        });
    }
}
