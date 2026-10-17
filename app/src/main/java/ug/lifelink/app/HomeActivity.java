package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import ug.lifelink.app.data.SampleData;
import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.model.User;
import ug.lifelink.app.ui.RecordAdapter;
import ug.lifelink.app.util.Eligibility;
import ug.lifelink.app.util.SessionManager;

/**
 * Home screen. Milestone 1: shows a greeting, a status card and a list of SAMPLE records
 * through an adapter. The data arrives from Login in the launching Intent (extras).
 * Milestone 2 swaps the sample list for a dynamic collection; Milestone 3 for the database.
 */
public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "ug.lifelink.app.extra.USER_ID";
    public static final String EXTRA_USER_NAME = "ug.lifelink.app.extra.USER_NAME";
    public static final String EXTRA_USER_ROLE = "ug.lifelink.app.extra.USER_ROLE";
    public static final String EXTRA_BLOOD_GROUP = "ug.lifelink.app.extra.BLOOD_GROUP";

    private TextView tvGreeting;
    private TextView tvRoleBadge;
    private TextView tvStatusTitle;
    private TextView tvStatusDetail;
    private TextView tvListHeader;
    private ListView listRecords;
    private ImageButton btnLogout;

    private boolean isHospital;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        long userId = intent.getLongExtra(EXTRA_USER_ID, -1L);
        if (userId < 0) {
            // Opened without a signed-in user (should not happen): send them to Login.
            goToLogin();
            return;
        }
        String fullName = intent.getStringExtra(EXTRA_USER_NAME);
        String role = intent.getStringExtra(EXTRA_USER_ROLE);
        String bloodGroup = intent.getStringExtra(EXTRA_BLOOD_GROUP);
        isHospital = User.ROLE_HOSPITAL.equals(role);

        setContentView(R.layout.activity_home);
        bindViews();

        String firstName = fullName == null ? "" : fullName.trim().split("\\s+")[0];
        tvGreeting.setText(getString(R.string.home_greeting, firstName));
        tvRoleBadge.setText(isHospital ? R.string.role_badge_hospital : R.string.role_badge_donor);
        tvListHeader.setText(isHospital ? R.string.list_header_hospital : R.string.list_header_donor);

        final long now = System.currentTimeMillis();
        List<BloodRecord> records = SampleData.forRole(role, bloodGroup, userId, now);
        bindStatusCard(records, now);

        final RecordAdapter adapter = new RecordAdapter(this, records);
        listRecords.setAdapter(adapter);
        listRecords.setEmptyView(findViewById(R.id.tvEmpty));
        listRecords.setOnItemClickListener((parent, view, position, id) -> {
            BloodRecord record = adapter.getItem(position);
            if (record != null) {
                Toast.makeText(this, getString(R.string.record_selected, record.getFacility()),
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    private void bindViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvRoleBadge = findViewById(R.id.tvRoleBadge);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusDetail = findViewById(R.id.tvStatusDetail);
        tvListHeader = findViewById(R.id.tvListHeader);
        listRecords = findViewById(R.id.listRecords);
        btnLogout = findViewById(R.id.btnLogout);
    }

    /** Donor: eligibility from the newest donation. Hospital: count of open requests. */
    private void bindStatusCard(List<BloodRecord> records, long now) {
        if (isHospital) {
            int open = 0;
            for (BloodRecord r : records) {
                if (BloodRecord.STATUS_OPEN.equals(r.getStatus())
                        || BloodRecord.STATUS_URGENT.equals(r.getStatus())) {
                    open++;
                }
            }
            tvStatusTitle.setText(R.string.status_hospital_title);
            tvStatusDetail.setText(getString(R.string.status_hospital_detail, open));
            return;
        }

        long lastDonation = -1L;
        for (BloodRecord r : records) {
            if (BloodRecord.KIND_DONATION.equals(r.getKind()) && r.getCreatedAt() > lastDonation) {
                lastDonation = r.getCreatedAt();
            }
        }

        if (lastDonation < 0) {
            tvStatusTitle.setText(R.string.status_first_title);
            tvStatusDetail.setText(R.string.status_first_detail);
            return;
        }

        int daysLeft = Eligibility.daysUntilEligible(lastDonation, now);
        if (daysLeft == 0) {
            tvStatusTitle.setText(R.string.status_eligible_title);
            tvStatusDetail.setText(getString(R.string.status_eligible_detail,
                    Eligibility.daysSince(lastDonation, now)));
        } else {
            tvStatusTitle.setText(R.string.status_wait_title);
            tvStatusDetail.setText(getString(R.string.status_wait_detail, daysLeft));
        }
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
