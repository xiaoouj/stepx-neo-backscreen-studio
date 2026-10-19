package com.workbuddy.backscreen;

/** Output ownership is inferred from actual playback, never from result.success or null. */
final class HandoffPolicy {
    static final long QUIET_MS = 600;
    private boolean waiting, dirty, currentKnownSafe;
    private long safeSince = -1;
    private int restores;
    private String observedType = "unknown", observedState = "unknown";

    void hint() { waiting = true; dirty = true; currentKnownSafe = false; safeSince = -1; }
    void observe(String type, String state, String ownState, long now) {
        currentKnownSafe = false;
        if (type == null) return; // Screen off / gated / unavailable is not an idle signal.
        observedType = type; observedState = state == null ? "" : state;
        boolean busy;
        if ("os_state".equals(type) || "input".equals(type)) busy = true;
        else if ("agent".equals(type) || "custom".equals(type)) {
            boolean idle = ("custom".equals(type) && observedState.isEmpty()) || observedState.startsWith("idle")
                    || "fallback".equals(observedState) || observedState.startsWith("basic_")
                    || "custom".equals(observedState);
            boolean self = !waiting && ownState != null && ownState.equals(observedState);
            busy = !idle && !self;
        } else busy = true; // Unknown playback types must retain their priority.
        if (busy) { waiting = true; dirty = true; safeSince = -1; }
        else { currentKnownSafe = true; if (waiting && safeSince < 0) safeSince = now; }
    }
    boolean shouldYield(long now) {
        if (waiting && currentKnownSafe && safeSince >= 0 && now-safeSince >= QUIET_MS) {
            waiting = false; safeSince = -1; dirty = true; restores++;
        }
        return waiting;
    }
    boolean takeInvalidation() { boolean value = dirty; dirty = false; return value; }
    boolean waiting() { return waiting; }
    int restores() { return restores; }
    String describe() { return "yielding="+waiting+" observedType="+observedType+" observedState="+observedState+" restores="+restores; }
    static boolean isExternalResult(String type, String state, String ownState) {
        if ("custom".equals(type)) return state != null && state.startsWith("basic_")
                && (ownState == null || !(ownState.startsWith("idle") || "fallback".equals(ownState)));
        return type != null && !("agent".equals(type) && ownState != null && ownState.equals(state));
    }
}
