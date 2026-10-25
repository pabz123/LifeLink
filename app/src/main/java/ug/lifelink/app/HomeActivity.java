package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.data.AppData;
import ug.lifelink.app.data.RecordRepository;
import ug.lifelink.app.data.SampleData;
import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.User;
import ug.lifelink.app.ui.ConfirmDialogFragment;
import ug.lifelink.app.ui.EligibilityRingView;
import ug.lifelink.app.ui.RecordAdapter;
import ug.lifelink.app.ui.StatusLabels;
import ug.lifelink.app.util.BloodCompatibility;
import ug.lifelink.app.util.BloodGroups;
import ug.lifelink.app.util.Eligibility;
import ug.lifelink.app.util.SessionManager;

/**
 * Home screen. Top bar (avatar, greeting, log out) above one scrolling list. The list's
 * header holds the hero card (donor eligibility ring, or open requests for hospital staff)
 * and the urgent requests matched to the donor's blood group.
 *
 * Authorisation: only the user id arrives in the Intent. The name, role and blood group are
 * loaded from the database, so the screen never trusts values passed from elsewhere. If the
 * id no longer exists, the user is sent back to Login.
 *
 * Milestone 2: the records come from RecordRepository, a collection the user changes with the
 * add and edit screens (tap a record to edit it, tap Add to create one). Tapping an urgent
 * request opens its details. The three-dot menu holds the app-wide actions. Milestone 3
 * replaces the repository's storage with SQLite and Milestone 4 replaces the urgent requests
 * with the REST API.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final String KEY_CONFIRM_LOGOUT = "confirm_logout";

    public static final String EXTRA_USER_ID = "ug.lifelink.app.extra.USER_ID";

    // top bar
    private TextView tvAvatar;
    private TextView tvGreeting;
    private TextView tvName;
    private ImageButton btnMenu;
    private ListView listRecords;

    // list header
    private EligibilityRingView ringProgress;
    private TextView tvRingValue;
    private TextView tvRingLabel;
    private TextView tvStatusTitle;
    private TextView tvStatusDetail;
    private TextView tvBloodChip;
    private TextView tvUrgentHeader;
    private TextView tvUrgentHint;
    private LinearLayout llUrgent;
    private TextView tvListHeader;
    private TextView tvEmptyRecords;
    private Button btnAddRecord;

    private RecordAdapter adapter;
    private UserRepository users;
    private RecordRepository recordStore;
    private User currentUser;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    /**
     * Receives the answer from the add/edit and details screens. Registered as a field so it
     * exists before the Activity starts, and again after every rotation.
     */
    private final ActivityResultLauncher<Intent> screenLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (currentUser != null) {
                    refreshRecords(); // the list may have changed, whatever the result code
                }
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    String what = data.getStringExtra(RecordEditActivity.EXTRA_RESULT);
                    if (RecordEditActivity.RESULT_DELETED.equals(what)) {
                        Toast.makeText(this, R.string.record_deleted, Toast.LENGTH_SHORT).show();
                    } else if (RecordEditActivity.RESULT_SAVED.equals(what)) {
                        Toast.makeText(this, R.string.record_saved, Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final long userId = getIntent().getLongExtra(EXTRA_USER_ID, -1L);
        if (userId < 0) {
            goToLogin(); // opened without a signed-in user (should not happen)
            return;
        }

        setContentView(R.layout.activity_home);
        bindTopBar();
        addAndBindHeader();

        adapter = new RecordAdapter(this, new ArrayList<BloodRecord>());
        listRecords.setAdapter(adapter);
        listRecords.setOnItemClickListener((parent, view, position, id) -> {
            // The header is position 0, so list positions are shifted by the header count.
            int index = position - listRecords.getHeaderViewsCount();
            BloodRecord record = index >= 0 && index < adapter.getCount() ? adapter.getItem(index) : null;
            if (record != null && currentUser != null) {
                openRecordForm(record.getId());
            }
        });
        btnMenu.setOnClickListener(v -> showMenu());
        btnAddRecord.setOnClickListener(v -> openRecordForm(-1L));

        // The logout confirmation reports back here. Registered on every create so it also
        // works after a rotation while the dialog is open.
        getSupportFragmentManager().setFragmentResultListener(KEY_CONFIRM_LOGOUT, this,
                (requestKey, result) -> logout());

        users = new UserRepository(this);
        recordStore = AppData.records(this);
        loadUser(userId);
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ setup

    private void bindTopBar() {
        tvAvatar = findViewById(R.id.tvAvatar);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvName = findViewById(R.id.tvName);
        btnMenu = findViewById(R.id.btnMenu);
        listRecords = findViewById(R.id.listRecords);
    }

    private void addAndBindHeader() {
        View header = LayoutInflater.from(this).inflate(R.layout.header_home, listRecords, false);
        // false = the header itself is not selectable. It must be added BEFORE setAdapter.
        listRecords.addHeaderView(header, null, false);

        ringProgress = header.findViewById(R.id.ringProgress);
        tvRingValue = header.findViewById(R.id.tvRingValue);
        tvRingLabel = header.findViewById(R.id.tvRingLabel);
        tvStatusTitle = header.findViewById(R.id.tvStatusTitle);
        tvStatusDetail = header.findViewById(R.id.tvStatusDetail);
        tvBloodChip = header.findViewById(R.id.tvBloodChip);
        tvUrgentHeader = header.findViewById(R.id.tvUrgentHeader);
        tvUrgentHint = header.findViewById(R.id.tvUrgentHint);
        llUrgent = header.findViewById(R.id.llUrgent);
        tvListHeader = header.findViewById(R.id.tvListHeader);
        tvEmptyRecords = header.findViewById(R.id.tvEmptyRecords);
        btnAddRecord = header.findViewById(R.id.btnAddRecord);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads the user and their records off the main thread (the database is on disk). New
     * accounts are given the sample records here, once, before the list is read.
     */
    private void loadUser(final long userId) {
        worker.execute(() -> {
            User user = null;
            List<BloodRecord> records = null;
            boolean failed = false;
            try {
                user = users.findById(userId);
                if (user != null) {
                    recordStore.seedOnce(user.getId(), SampleData.forRole(user.getRole(),
                            user.getBloodGroup(), user.getId(), System.currentTimeMillis()));
                    records = recordStore.listFor(user.getId());
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not load the user or their records", e);
                failed = true;
            }
            final User loadedUser = user;
            final List<BloodRecord> loadedRecords = records;
            final boolean loadFailed = failed;
            runOnUiThread(() -> onUserLoaded(loadedUser, loadedRecords, loadFailed));
        });
    }

    private void onUserLoaded(User user, List<BloodRecord> records, boolean loadFailed) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (loadFailed) {
            // A database problem is not the same as a missing account: do not sign the user out.
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (user == null) {
            new SessionManager(this).clear();
            goToLogin(); // the account no longer exists
            return;
        }

        currentUser = user;
        tvGreeting.setText(greetingForNow());
        tvName.setText(user.getFirstName());
        tvAvatar.setText(initialsOf(user.getFullName()));
        btnAddRecord.setText(user.isHospital() ? R.string.menu_add_request : R.string.menu_add_donation);

        showRecords(records);

        if (user.isHospital()) {
            tvListHeader.setText(R.string.list_header_hospital);
            tvUrgentHeader.setVisibility(View.GONE);
            tvUrgentHint.setVisibility(View.GONE);
            llUrgent.setVisibility(View.GONE);
        } else {
            tvListHeader.setText(R.string.list_header_donor);
            bindUrgentRequests(user);
        }
    }

    /** Reloads this user's records from the database (off the main thread) and shows them. */
    private void refreshRecords() {
        final long userId = currentUser.getId();
        worker.execute(() -> {
            List<BloodRecord> records = null;
            try {
                records = recordStore.listFor(userId);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not reload the records", e);
            }
            final List<BloodRecord> loaded = records;
            runOnUiThread(() -> {
                if (loaded != null && !isFinishing() && !isDestroyed()) {
                    showRecords(loaded);
                }
            });
        });
    }

    /** Puts the given records in the list and refreshes the hero card. Main thread only. */
    private void showRecords(List<BloodRecord> records) {
        final long now = System.currentTimeMillis();
        adapter.clear();
        adapter.addAll(records);
        tvEmptyRecords.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        if (currentUser.isHospital()) {
            bindHospitalHero(records);
        } else {
            bindDonorHero(currentUser, records, now);
        }
    }

    // ------------------------------------------------------------------ menu, forms, details

    private void showMenu() {
        if (currentUser == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, btnMenu);
        popup.getMenuInflater().inflate(R.menu.menu_home, popup.getMenu());
        popup.getMenu().findItem(R.id.action_add).setTitle(
                currentUser.isHospital() ? R.string.menu_add_request : R.string.menu_add_donation);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_add) {
                openRecordForm(-1L);
                return true;
            } else if (id == R.id.action_about) {
                ConfirmDialogFragment.newInfo(R.string.about_title, R.string.about_message)
                        .show(getSupportFragmentManager(), ConfirmDialogFragment.TAG);
                return true;
            } else if (id == R.id.action_logout) {
                confirmLogout();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** recordId -1 opens the form empty to add a record; any other id opens it to edit. */
    private void openRecordForm(long recordId) {
        Intent form = new Intent(this, RecordEditActivity.class)
                .putExtra(RecordEditActivity.EXTRA_USER_ID, currentUser.getId())
                .putExtra(RecordEditActivity.EXTRA_KIND, currentUser.isHospital()
                        ? BloodRecord.KIND_REQUEST : BloodRecord.KIND_DONATION)
                .putExtra(RecordEditActivity.EXTRA_DEFAULT_GROUP, currentUser.getBloodGroup());
        if (recordId >= 0) {
            form.putExtra(RecordEditActivity.EXTRA_RECORD_ID, recordId);
        }
        screenLauncher.launch(form);
    }

    private void openRequestDetail(UrgentRequest request) {
        screenLauncher.launch(RequestDetailActivity.createIntent(
                this, currentUser.getId(), currentUser.getBloodGroup(), request));
    }

    private void confirmLogout() {
        ConfirmDialogFragment.newConfirmation(KEY_CONFIRM_LOGOUT, R.string.logout_confirm_title,
                R.string.logout_confirm_message, R.string.action_logout)
                .show(getSupportFragmentManager(), ConfirmDialogFragment.TAG);
    }

    // ------------------------------------------------------------------ hero card

    private void bindDonorHero(User user, List<BloodRecord> records, long now) {
        String group = user.getBloodGroup();
        boolean known = BloodGroups.isKnown(group);
        tvBloodChip.setText(known ? getString(R.string.chip_blood_group, group)
                : getString(R.string.chip_blood_group_unknown));

        long lastDonation = -1L;
        for (BloodRecord r : records) {
            if (BloodRecord.KIND_DONATION.equals(r.getKind()) && r.getCreatedAt() > lastDonation) {
                lastDonation = r.getCreatedAt();
            }
        }

        if (lastDonation < 0) {
            // Never donated: ready now. A donor with an unknown group is told how it gets confirmed.
            ringProgress.setProgress(1f);
            showReadyRing();
            tvStatusTitle.setText(R.string.status_first_title);
            tvStatusDetail.setText(known ? R.string.status_first_detail
                    : R.string.status_first_detail_unknown);
            return;
        }

        int daysLeft = Eligibility.daysUntilEligible(lastDonation, now);
        int daysSince = Eligibility.daysSince(lastDonation, now);
        if (daysLeft == 0) {
            ringProgress.setProgress(1f);
            showReadyRing();
            tvStatusTitle.setText(R.string.status_eligible_title);
            tvStatusDetail.setText(getString(R.string.status_eligible_detail, daysText(daysSince)));
        } else {
            ringProgress.setProgress(daysSince / (float) Eligibility.INTERVAL_DAYS);
            tvRingValue.setText(String.valueOf(daysLeft));
            tvRingLabel.setVisibility(View.VISIBLE);
            tvRingLabel.setText(getResources().getQuantityString(R.plurals.ring_days_to_go, daysLeft));
            tvStatusTitle.setText(R.string.status_wait_title);
            tvStatusDetail.setText(getString(R.string.status_wait_detail, daysText(daysLeft)));
        }
    }

    private void showReadyRing() {
        tvRingValue.setText(R.string.ring_value_ready);
        tvRingLabel.setVisibility(View.GONE);
    }

    private void bindHospitalHero(List<BloodRecord> records) {
        int open = 0;
        int fulfilled = 0;
        for (BloodRecord r : records) {
            if (BloodRecord.STATUS_FULFILLED.equals(r.getStatus())) {
                fulfilled++;
            } else if (BloodRecord.STATUS_OPEN.equals(r.getStatus())
                    || BloodRecord.STATUS_URGENT.equals(r.getStatus())) {
                open++;
            }
        }
        ringProgress.setProgress(records.isEmpty() ? 0f : fulfilled / (float) records.size());
        tvRingValue.setText(String.valueOf(open));
        tvRingLabel.setVisibility(View.VISIBLE);
        tvRingLabel.setText(R.string.ring_label_open);
        tvStatusTitle.setText(R.string.status_hospital_title);
        tvStatusDetail.setText(getString(R.string.status_hospital_detail, open));
        tvBloodChip.setText(R.string.chip_hospital);
    }

    // ------------------------------------------------------------------ urgent requests

    /** Shows only the requests this donor's blood can help with, nearest first. */
    private void bindUrgentRequests(User user) {
        tvUrgentHeader.setVisibility(View.VISIBLE);
        llUrgent.setVisibility(View.VISIBLE);
        llUrgent.removeAllViews();

        if (!BloodGroups.isKnown(user.getBloodGroup())) {
            tvUrgentHint.setText(R.string.urgent_hint_unknown);
            tvUrgentHint.setVisibility(View.VISIBLE);
            return;
        }

        List<UrgentRequest> matched = new ArrayList<>();
        for (UrgentRequest request : SampleData.urgentRequests()) {
            if (BloodCompatibility.canDonateTo(user.getBloodGroup(), request.getBloodGroup())) {
                matched.add(request);
            }
        }
        Collections.sort(matched, (a, b) -> Double.compare(a.getDistanceKm(), b.getDistanceKm()));

        if (matched.isEmpty()) {
            tvUrgentHint.setText(R.string.urgent_none);
            tvUrgentHint.setVisibility(View.VISIBLE);
            return;
        }
        tvUrgentHint.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final UrgentRequest request : matched) {
            View row = inflater.inflate(R.layout.item_record, llUrgent, false);
            ((TextView) row.findViewById(R.id.tvGroup)).setText(BloodGroups.badgeText(request.getBloodGroup()));
            ((TextView) row.findViewById(R.id.tvTitle)).setText(request.getFacility());

            String units = getResources().getQuantityString(R.plurals.record_units,
                    request.getUnits(), request.getUnits());
            String distance = getString(R.string.distance_km, request.getDistanceKm());
            ((TextView) row.findViewById(R.id.tvSubtitle))
                    .setText(getString(R.string.urgent_subtitle, units, distance));
            StatusLabels.apply(this, (TextView) row.findViewById(R.id.tvStatus), request.getStatus());

            row.setOnClickListener(v -> openRequestDetail(request));
            llUrgent.addView(row);
        }
    }

    // ------------------------------------------------------------------ helpers

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return getString(R.string.greeting_morning);
        }
        return getString(hour < 17 ? R.string.greeting_afternoon : R.string.greeting_evening);
    }

    private String daysText(int days) {
        return getResources().getQuantityString(R.plurals.days_count, days, days);
    }

    /** "Mulungi Precious Pabire" becomes "MP"; a single name gives one letter. */
    private static String initialsOf(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private void logout() {
        new SessionManager(this).clear();
        Toast.makeText(this, R.string.logout_done, Toast.LENGTH_SHORT).show();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        // Clear the whole back stack so Back cannot return to a signed-in screen.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
