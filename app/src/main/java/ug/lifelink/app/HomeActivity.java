package ug.lifelink.app;

import android.Manifest;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.provider.Settings;
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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.alert.AlertNotifier;
import ug.lifelink.app.alert.AlertScheduler;
import ug.lifelink.app.alert.AlertSettings;
import ug.lifelink.app.data.AppData;
import ug.lifelink.app.data.RecordRepository;
import ug.lifelink.app.data.RequestCache;
import ug.lifelink.app.data.SampleData;
import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.location.DeviceLocation;
import ug.lifelink.app.location.LocationStore;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.net.RequestFeedClient;
import ug.lifelink.app.net.RequestFeedParser;
import ug.lifelink.app.model.User;
import ug.lifelink.app.ui.ConfirmDialogFragment;
import ug.lifelink.app.ui.EligibilityRingView;
import ug.lifelink.app.ui.RecordAdapter;
import ug.lifelink.app.ui.StatusLabels;
import ug.lifelink.app.util.BloodGroups;
import ug.lifelink.app.util.Eligibility;
import ug.lifelink.app.util.RequestMatcher;
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
 * replaces the repository's storage with SQLite. Milestone 4 reads the urgent requests from the
 * live feed, keeps a copy in SQLite for offline use, and works out distances on the phone.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final String KEY_CONFIRM_LOGOUT = "confirm_logout";
    /** The feed is fetched again only when the saved copy is older than this. */
    private static final long FEED_STALE_MS = 5L * 60 * 1000;

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
    private TextView tvFeedStatus;
    private TextView tvLocationPrompt;
    private Button btnLocation;

    private RecordAdapter adapter;
    private UserRepository users;
    private RecordRepository recordStore;
    private RequestCache requestCache;
    private LocationStore locationStore;
    private long lastFeedAttempt;
    /** True after a download failed; the next time the network returns, the feed is fetched at once. */
    private boolean feedOffline;
    private ConnectivityManager.NetworkCallback networkCallback;
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

    /** Result of the location permission dialog. Granted: refresh with the real position. */
    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (currentUser != null && !currentUser.isHospital()) {
                    bindUrgentRequests(currentUser);
                    askNotificationPermissionOnce();
                }
            });

    /** Result of the notification permission dialog. A "no" simply means no alerts. */
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });

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
        requestCache = new RequestCache(this);
        locationStore = new LocationStore(this);
        loadUser(userId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Settings (or another screen): re-check permission and the saved requests.
        if (currentUser != null && !currentUser.isHospital()) {
            bindUrgentRequests(currentUser);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        watchNetwork();
    }

    @Override
    protected void onStop() {
        unwatchNetwork();
        super.onStop();
    }

    /** While Home is on screen: when a connection appears after a failed download, sync at once. */
    private void watchNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null || networkCallback != null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    if (feedOffline && currentUser != null && !currentUser.isHospital()
                            && !isFinishing() && !isDestroyed()) {
                        lastFeedAttempt = 0L; // forces a fresh download
                        bindUrgentRequests(currentUser);
                    }
                });
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            networkCallback = null; // the periodic job and the next resume still sync
        }
    }

    private void unwatchNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null && networkCallback != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException e) {
                Log.w(TAG, "Network callback was not registered", e);
            }
        }
        networkCallback = null;
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
        tvFeedStatus = header.findViewById(R.id.tvFeedStatus);
        tvLocationPrompt = header.findViewById(R.id.tvLocationPrompt);
        btnLocation = header.findViewById(R.id.btnLocation);
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
            tvFeedStatus.setVisibility(View.GONE);
            hideLocationPrompt();
        } else {
            tvListHeader.setText(R.string.list_header_donor);
            bindUrgentRequests(user);
            AlertScheduler.schedule(this); // background check about every 15 minutes
        }
    }

    /**
     * Asks to show notifications, once, on Android 13 and later. Called after the location
     * question so two permission dialogs never appear together.
     */
    private void askNotificationPermissionOnce() {
        AlertSettings settings = new AlertSettings(this);
        if (android.os.Build.VERSION.SDK_INT >= 33 && !AlertNotifier.canNotify(this)
                && !settings.wasNotificationPermissionAsked()) {
            settings.markNotificationPermissionAsked();
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
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
        popup.getMenu().findItem(R.id.action_demo).setVisible(!currentUser.isHospital());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_add) {
                openRecordForm(-1L);
                return true;
            } else if (id == R.id.action_demo) {
                screenLauncher.launch(new Intent(this, DemoActivity.class)
                        .putExtra("ug.lifelink.app.extra.USER_ID", currentUser.getId()));
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

    /**
     * Shows the requests this donor's blood can help with, most urgent first. Order of work:
     * show the saved copy straight away (works offline), then refresh from the feed when the
     * copy is stale, then add distances once a position is known.
     */
    private void bindUrgentRequests(final User user) {
        tvUrgentHeader.setVisibility(View.VISIBLE);
        llUrgent.setVisibility(View.VISIBLE);

        if (!BloodGroups.isKnown(user.getBloodGroup())) {
            llUrgent.removeAllViews();
            tvUrgentHint.setText(R.string.urgent_hint_unknown);
            tvUrgentHint.setVisibility(View.VISIBLE);
            tvFeedStatus.setVisibility(View.GONE);
            hideLocationPrompt();
            return;
        }

        final boolean refresh = System.currentTimeMillis() - lastFeedAttempt > FEED_STALE_MS;
        if (refresh) {
            lastFeedAttempt = System.currentTimeMillis();
        }
        worker.execute(() -> {
            boolean offline = false;
            int skipped = 0;
            List<UrgentRequest> saved = null;
            long fetchedAt = 0L;
            try {
                long now = System.currentTimeMillis();
                if (refresh || requestCache.lastFetchedAt() == 0L
                        || now - requestCache.lastFetchedAt() > FEED_STALE_MS) {
                    try {
                        RequestFeedParser.Result result = RequestFeedClient.fetch();
                        requestCache.replaceAll(result.requests, now);
                        skipped = result.skipped;
                    } catch (java.io.IOException e) {
                        Log.w(TAG, "Feed not reachable, using the saved copy", e);
                        offline = true;
                    }
                }
                saved = requestCache.listAll();
                fetchedAt = requestCache.lastFetchedAt();
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not read the saved requests", e);
            }
            final List<UrgentRequest> all = saved;
            final boolean wasOffline = offline;
            final int bad = skipped;
            final long at = fetchedAt;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                feedOffline = wasOffline;
                if (wasOffline) {
                    // Also ask Android to run one check the moment a connection returns,
                    // even if the app is closed by then.
                    AlertScheduler.runWhenOnline(this);
                }
                renderUrgent(user, all, at, wasOffline, bad);
                findPosition(user, all, at, wasOffline, bad);
            });
        });
    }

    /** Asks for a fresh position when allowed; otherwise offers the permission prompt. */
    private void findPosition(final User user, final List<UrgentRequest> all, final long at,
                              final boolean offline, final int bad) {
        if (locationStore.isDemoOn()) {
            hideLocationPrompt();
            return;
        }
        if (DeviceLocation.hasPermission(this)) {
            hideLocationPrompt();
            askNotificationPermissionOnce();
            DeviceLocation.get(this, location -> {
                if (location == null || isFinishing() || isDestroyed()) {
                    return;
                }
                locationStore.saveLast(location.getLatitude(), location.getLongitude(),
                        System.currentTimeMillis());
                renderUrgent(user, all, at, offline, bad);
            });
            return;
        }
        if (!locationStore.wasPermissionAsked()) {
            // First time: show the system dialog straight away. The button below is only for
            // asking again after a "no", or for opening Settings after "Don't ask again".
            locationStore.markPermissionAsked();
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            return;
        }
        showLocationPrompt();
    }

    private void showLocationPrompt() {
        final boolean askedBefore = locationStore.wasPermissionAsked();
        final boolean canAskAgain = androidx.core.app.ActivityCompat
                .shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        // Asked before and no rationale offered means "Don't ask again": only Settings can help.
        final boolean needsSettings = askedBefore && !canAskAgain;
        tvLocationPrompt.setText(needsSettings ? R.string.location_denied_hint : R.string.location_prompt);
        tvLocationPrompt.setVisibility(View.VISIBLE);
        btnLocation.setText(needsSettings ? R.string.location_open_settings : R.string.location_allow);
        btnLocation.setVisibility(View.VISIBLE);
        btnLocation.setOnClickListener(v -> {
            if (needsSettings) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } else {
                locationStore.markPermissionAsked();
                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        });
    }

    private void hideLocationPrompt() {
        tvLocationPrompt.setVisibility(View.GONE);
        btnLocation.setVisibility(View.GONE);
    }

    /** Matches the saved requests to this donor and draws the rows. Main thread only. */
    private void renderUrgent(User user, List<UrgentRequest> all, long fetchedAt,
                              boolean offline, int skipped) {
        llUrgent.removeAllViews();

        DateFormat time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        String when = fetchedAt > 0 ? time.format(new Date(fetchedAt)) : "";
        String status;
        if (fetchedAt == 0L) {
            status = getString(R.string.feed_never);
        } else if (offline) {
            status = getString(R.string.feed_offline, when);
        } else {
            status = getString(R.string.feed_updated, when);
        }
        if (skipped > 0) {
            status = status + " " + getString(R.string.feed_skipped, skipped);
        }
        tvFeedStatus.setText(status);
        tvFeedStatus.setVisibility(View.VISIBLE);

        if (all == null || all.isEmpty()) {
            tvUrgentHint.setText(fetchedAt == 0L ? R.string.urgent_offline_empty : R.string.urgent_none);
            tvUrgentHint.setVisibility(View.VISIBLE);
            return;
        }

        double[] where = locationStore.effective();
        List<UrgentRequest> matched = RequestMatcher.match(user.getBloodGroup(), where != null,
                where == null ? 0 : where[0], where == null ? 0 : where[1],
                System.currentTimeMillis(), all);

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
            String distance = request.hasDistance()
                    ? getString(R.string.distance_km, request.getDistanceKm())
                    : getString(R.string.distance_unknown);
            ((TextView) row.findViewById(R.id.tvSubtitle))
                    .setText(getString(R.string.urgent_subtitle, units, distance));
            StatusLabels.applyUrgency(this, (TextView) row.findViewById(R.id.tvStatus), request.getUrgency());

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
        AlertScheduler.cancel(this);
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
