package com.kioskmdm;

/** Pure timing state: repeated downs never extend a hold; every release cancels it. */
final class BackHoldState {
    static final long HOLD_MS = 5000;
    private long downAt = -1;
    private boolean fired;

    boolean down(long now, boolean repeat) {
        if (repeat || downAt >= 0) return false;
        downAt = now;
        fired = false;
        return true;
    }

    boolean fireIfDue(long now) {
        if (downAt < 0 || fired || now - downAt < HOLD_MS) return false;
        fired = true;
        return true;
    }

    void cancel() { downAt = -1; fired = false; }
}
