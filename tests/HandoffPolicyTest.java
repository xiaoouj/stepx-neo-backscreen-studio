package com.workbuddy.backscreen;

public final class HandoffPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        HandoffPolicy p = new HandoffPolicy();
        p.observe("custom","idle",null,0);
        check(!p.shouldYield(0) && !p.takeInvalidation(), "Unchanged full battery should remain cached");
        p.observe("agent","listening",null,100);
        check(p.shouldYield(100), "Must yield to listening");
        p.observe(null,null,null,50000);
        check(p.shouldYield(50000), "A gated screen is not an assistant exit");
        p.observe("agent","thinking",null,50100);
        check(p.shouldYield(50100), "Thinking must keep its priority");
        p.observe("custom","basic_text",null,50200);
        check(p.shouldYield(50799), "Do not restore inside a short transition gap");
        p.observe(null,null,null,51000);
        check(p.shouldYield(51000), "A stale safe observation cannot resolve unknown playback");
        p.observe("custom","basic_text",null,51200);
        check(!p.shouldYield(51200) && p.restores()==1, "Restore after confirmed idle");
        check(p.takeInvalidation(), "Static/full battery must be resent even when pixels did not change");
        p.observe("custom","idle",null,52000);
        check(!p.shouldYield(52000) && !p.takeInvalidation(), "No self-refresh loop");
        p.observe("custom","listening",null,53000);
        check(p.shouldYield(53000), "Our custom frame must not hide an active underlying assistant");
        p.observe("agent","idle",null,54000); p.observe("agent","speaking",null,54100);
        check(p.shouldYield(56000), "Speech after a short idle gap must keep priority");
        p.observe("agent","idle",null,57000); p.observe("agent","idle",null,57600);
        check(!p.shouldYield(57600) && p.restores()==2, "Second takeover restoration");
        p.hint(); p.observe(null,null,null,60000);
        check(p.shouldYield(60000), "Result callback alone cannot declare AI finished");
        p.observe("custom","basic_text",null,61000); p.observe("custom","basic_text",null,61600);
        check(!p.shouldYield(61600), "Result hint plus idle query should restore");
        HandoffPolicy own = new HandoffPolicy();
        own.observe("agent","thinking","thinking",0);
        check(!own.shouldYield(0), "A selected system-expression preset cannot block itself");
        own.observe("agent","wake_voice","thinking",100);
        own.observe("agent","thinking","thinking",1000);
        check(own.shouldYield(1000), "After takeover the same preset name cannot impersonate idle");
        check(!HandoffPolicy.isExternalResult("custom","idle",null), "Ignore own pixel result callbacks");
        check(HandoffPolicy.isExternalResult("agent","listening",null), "Re-query on assistant results");
        check(HandoffPolicy.isExternalResult("os_state","incoming_call",null), "Respect call results");
        check(!HandoffPolicy.isExternalResult("custom","basic_text","idle"), "A selected idle preset cannot chase its own basic-layer refresh");
        HandoffPolicy unknown = new HandoffPolicy(); unknown.observe("agent","",null,0);
        check(unknown.shouldYield(0), "An unnamed agent animation is not confirmed idle");
        System.out.println("PASS: assistant takeover, unknown/gated playback, quiet period, cache invalidation and self-callback isolation.");
    }
}
