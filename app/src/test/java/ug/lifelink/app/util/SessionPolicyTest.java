package ug.lifelink.app.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionPolicyTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void freshSessionIsActive() {
        assertTrue(SessionPolicy.isActive(NOW - 1000, NOW));
    }

    @Test
    public void exactlyAtTheTimeoutIsStillActive() {
        assertTrue(SessionPolicy.isActive(NOW - SessionPolicy.IDLE_TIMEOUT_MS, NOW));
    }

    @Test
    public void afterTheTimeoutIsEnded() {
        assertFalse(SessionPolicy.isActive(NOW - SessionPolicy.IDLE_TIMEOUT_MS - 1, NOW));
    }

    @Test
    public void neverActiveIsEnded() {
        assertFalse(SessionPolicy.isActive(0L, NOW));
        assertFalse(SessionPolicy.isActive(-5L, NOW));
    }

    @Test
    public void clockMovedBackwardsEndsTheSession() {
        assertFalse(SessionPolicy.isActive(NOW + 60_000, NOW));
    }
}
