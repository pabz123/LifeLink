package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.model.User;
import ug.lifelink.app.util.SessionManager;

/**
 * The launcher screen. It shows the brand for a moment, and meanwhile decides where to go:
 * Home for someone who chose "Keep me signed in", otherwise Login. Doing this check here
 * keeps LoginActivity focused on logging in.
 *
 * The splash is kept on screen for at least MIN_SPLASH_MS so the animation can finish,
 * but never longer than that once the check is done.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long MIN_SPLASH_MS = 1400L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable navigateRunnable = this::navigate;

    private long startedAt;
    private boolean resolved;     // true once we know where to go
    private User rememberedUser;  // null means "go to Login"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        startedAt = SystemClock.elapsedRealtime();

        animateIn();
        resolveDestination();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (resolved) {
            scheduleNavigation(); // we came back after being stopped
        }
    }

    @Override
    protected void onStop() {
        // Do not start another screen while this one is in the background.
        handler.removeCallbacks(navigateRunnable);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        worker.shutdown();
        super.onDestroy();
    }

    /** Logo fades and grows in, then the name and tagline follow. */
    private void animateIn() {
        View logo = findViewById(R.id.imgSplashLogo);
        View name = findViewById(R.id.tvSplashName);
        View tagline = findViewById(R.id.tvSplashTagline);

        logo.setAlpha(0f);
        logo.setScaleX(0.8f);
        logo.setScaleY(0.8f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600L).start();

        name.setAlpha(0f);
        name.animate().alpha(1f).setStartDelay(300L).setDuration(500L).start();

        tagline.setAlpha(0f);
        tagline.animate().alpha(1f).setStartDelay(500L).setDuration(500L).start();
    }

    private void resolveDestination() {
        final SessionManager session = new SessionManager(this);
        if (!session.hasRememberedUser()) {
            onResolved(null);
            return;
        }
        final long id = session.getRememberedUserId();
        worker.execute(() -> {
            User user = null;
            try {
                user = new UserRepository(getApplicationContext()).findById(id);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not load the remembered user", e);
            }
            final User result = user;
            runOnUiThread(() -> {
                if (result == null) {
                    session.clear(); // the account no longer exists
                }
                onResolved(result);
            });
        });
    }

    private void onResolved(User user) {
        rememberedUser = user;
        resolved = true;
        scheduleNavigation();
    }

    private void scheduleNavigation() {
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        long remaining = Math.max(0L, MIN_SPLASH_MS - elapsed);
        handler.removeCallbacks(navigateRunnable);
        handler.postDelayed(navigateRunnable, remaining);
    }

    private void navigate() {
        Intent intent;
        if (rememberedUser != null) {
            intent = new Intent(this, HomeActivity.class);
            intent.putExtra(HomeActivity.EXTRA_USER_ID, rememberedUser.getId());
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish(); // the splash must not be reachable with the Back button
    }
}
