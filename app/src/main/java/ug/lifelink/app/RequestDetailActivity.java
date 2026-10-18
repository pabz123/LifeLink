package ug.lifelink.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.ui.StatusLabels;
import ug.lifelink.app.util.BloodGroups;

/**
 * Details of one urgent request, with three ways to act on it that hand over to other apps:
 *   Call hospital   opens the phone's dialler with the number filled in (ACTION_DIAL)
 *   Open in Maps    opens a maps app searching for the hospital (ACTION_VIEW with a geo: URI)
 *   Share request   opens the Android share sheet (ACTION_SEND)
 * and one that continues inside the app: "I donated here" opens the add-donation form with the
 * hospital already filled in.
 *
 * Data passing: the request's fields travel in the Intent that Home builds with createIntent().
 *
 * Back button: Back (or the arrow) returns to Home with nothing changed. If a donation is saved
 * from here, this screen passes the result on to Home and closes, so Back from Home does not
 * return to a stale details screen.
 *
 * Why startActivity is wrapped in try/catch: an implicit Intent crashes the app with
 * ActivityNotFoundException when no installed app can handle it (for example a tablet with no
 * phone app). Catching it turns a crash into a clear message.
 */
public class RequestDetailActivity extends AppCompatActivity {

    private static final String EXTRA_USER_ID = "ug.lifelink.app.extra.USER_ID";
    private static final String EXTRA_DONOR_GROUP = "ug.lifelink.app.extra.DONOR_GROUP";
    private static final String EXTRA_FACILITY = "ug.lifelink.app.extra.FACILITY";
    private static final String EXTRA_GROUP = "ug.lifelink.app.extra.GROUP";
    private static final String EXTRA_UNITS = "ug.lifelink.app.extra.UNITS";
    private static final String EXTRA_DISTANCE = "ug.lifelink.app.extra.DISTANCE";
    private static final String EXTRA_STATUS = "ug.lifelink.app.extra.STATUS";
    private static final String EXTRA_NOTE = "ug.lifelink.app.extra.NOTE";
    private static final String EXTRA_PHONE = "ug.lifelink.app.extra.PHONE";

    /** Builds the Intent that opens this screen, so Home does not need to know the extra names. */
    public static Intent createIntent(Context context, long userId, String donorGroup,
                                      UrgentRequest request) {
        return new Intent(context, RequestDetailActivity.class)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_DONOR_GROUP, donorGroup)
                .putExtra(EXTRA_FACILITY, request.getFacility())
                .putExtra(EXTRA_GROUP, request.getBloodGroup())
                .putExtra(EXTRA_UNITS, request.getUnits())
                .putExtra(EXTRA_DISTANCE, request.getDistanceKm())
                .putExtra(EXTRA_STATUS, request.getStatus())
                .putExtra(EXTRA_NOTE, request.getNote())
                .putExtra(EXTRA_PHONE, request.getContactPhone());
    }

    private long userId;
    private String donorGroup;
    private String facility;
    private String group;
    private int units;
    private String phone;

    /** Receives the answer from the add-donation form and passes it on to Home. */
    private final ActivityResultLauncher<Intent> donationForm = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    setResult(RESULT_OK, result.getData());
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        userId = in.getLongExtra(EXTRA_USER_ID, -1L);
        facility = in.getStringExtra(EXTRA_FACILITY);
        group = in.getStringExtra(EXTRA_GROUP);
        if (userId < 0 || facility == null || group == null) {
            Toast.makeText(this, R.string.request_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        donorGroup = in.getStringExtra(EXTRA_DONOR_GROUP);
        units = in.getIntExtra(EXTRA_UNITS, 1);
        phone = in.getStringExtra(EXTRA_PHONE);
        double distanceKm = in.getDoubleExtra(EXTRA_DISTANCE, 0.0);
        String status = in.getStringExtra(EXTRA_STATUS);
        String reason = in.getStringExtra(EXTRA_NOTE);

        setContentView(R.layout.activity_request_detail);

        ((TextView) findViewById(R.id.tvGroup)).setText(BloodGroups.badgeText(group));
        ((TextView) findViewById(R.id.tvFacility)).setText(facility);
        StatusLabels.apply(this, (TextView) findViewById(R.id.tvStatus),
                status == null ? BloodRecord.STATUS_OPEN : status);
        ((TextView) findViewById(R.id.tvUnits)).setText(unitsText());
        ((TextView) findViewById(R.id.tvDistance)).setText(getString(R.string.distance_km, distanceKm));
        ((TextView) findViewById(R.id.tvReason)).setText(reason == null || reason.isEmpty() ? "-" : reason);

        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());
        findViewById(R.id.btnCall).setOnClickListener(v -> callHospital());
        findViewById(R.id.btnDirections).setOnClickListener(v -> openMaps());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareRequest());
        findViewById(R.id.btnDonatedHere).setOnClickListener(v -> openDonationForm());
    }

    private String unitsText() {
        return getResources().getQuantityString(R.plurals.record_units, units, units);
    }

    // ------------------------------------------------------------------ implicit intents

    private void callHospital() {
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(this, R.string.error_no_dialler, Toast.LENGTH_LONG).show();
            return;
        }
        // ACTION_DIAL only opens the dialler with the number typed in. The user still presses
        // the call button, so no CALL_PHONE permission is needed.
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        launch(dial, R.string.error_no_dialler);
    }

    private void openMaps() {
        Intent maps = new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(facility)));
        launch(maps, R.string.error_no_maps);
    }

    private void shareRequest() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, facility, unitsText(), group));
        launch(Intent.createChooser(send, getString(R.string.share_chooser_title)), R.string.error_no_share);
    }

    private void launch(Intent intent, int errorMessage) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ explicit intent

    private void openDonationForm() {
        Intent form = new Intent(this, RecordEditActivity.class)
                .putExtra(RecordEditActivity.EXTRA_USER_ID, userId)
                .putExtra(RecordEditActivity.EXTRA_KIND, BloodRecord.KIND_DONATION)
                .putExtra(RecordEditActivity.EXTRA_DEFAULT_GROUP, donorGroup)
                .putExtra(RecordEditActivity.EXTRA_PREFILL_FACILITY, facility);
        donationForm.launch(form);
    }
}
