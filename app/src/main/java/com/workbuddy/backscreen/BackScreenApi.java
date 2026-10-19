package com.workbuddy.backscreen;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.lang.reflect.Proxy;

/**
 * STEPX Neo 背屏 (MiniLED 17x23 点阵) 控制封装。
 *
 * 通过反射访问系统隐藏服务 display_ext (StepOSDisplayManagerService)。
 * 服务端 binder 方法不校验权限，客户端方法 hiddenapi 级别为
 * UNSUPPORTED (灰名单)，普通 App 可直接调用。
 *
 * 帧格式: int[391]，行优先 (y*17+x)，每个元素 = 该 LED 亮度 0..255。
 */
public final class BackScreenApi {

    public static final int W = 17;
    public static final int H = 23;
    public static final int N = W * H; // 391

    /** 物理点阵掩膜: 面板四角是圆角死区 (斜切深度约 3 点), 死区 LED 不存在 */
    public static final boolean[] DOT_OK = buildDotMask();

    private static boolean[] buildDotMask() {
        boolean[] m = new boolean[N];
        final int depth = 3;
        for (int y = 0; y < H; y++) {
            int ct = Math.max(0, depth - y);            // 顶部斜切宽度
            int cb = Math.max(0, depth - (H - 1 - y));  // 底部斜切宽度
            for (int x = 0; x < W; x++) {
                m[y * W + x] = x >= ct && x <= W - 1 - ct
                            && x >= cb && x <= W - 1 - cb;
            }
        }
        return m;
    }

    private static final String TAG = "BackScreenApi";
    private static final String SERVICE_NAME = "display_ext";

    private static BackScreenApi sInstance;

    private Object mProxy;   // IStepOSDisplayManager (binder proxy)
    private Object mManager; // StepOSDisplayManager (framework client, 若可用)
    public final boolean available;
    public final String detail;
    private Object animationListener;
    public interface AnimationObserver { void onResult(AnimationInfo info, boolean success); }
    public static final class AnimationInfo {
        public final String id, name, state, type;
        AnimationInfo(String id, String name, String state, String type) {
            this.id = id; this.name = name; this.state = state; this.type = type;
        }
    }
    private static String infoField(Object info, String getter) throws Exception {
        Object value = info.getClass().getMethod(getter).invoke(info);
        return value == null ? "" : value.toString();
    }
    private static AnimationInfo readInfo(Object info) throws Exception {
        return info == null ? null : new AnimationInfo(infoField(info,"getId"), infoField(info,"getName"),
                infoField(info,"getState"), infoField(info,"getType"));
    }
    /** Null is unknown: playback may be gated while the underlying AI remains active. */
    public AnimationInfo animationInfo() {
        try { return readInfo(call("getBackScreenAnimationInfo")); }
        catch (Exception e) { return null; }
    }
    public boolean registerAnimationObserver(Executor executor, AnimationObserver observer) {
        unregisterAnimationObserver();
        if (mManager == null) return false;
        try {
            Method register = findMethod(mManager.getClass(), "registerBackScreenAnimationListener", 2);
            if (register == null) return false;
            Class<?> listener = register.getParameterTypes()[1];
            if (!listener.isInterface()) return false;
            animationListener = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return args != null && proxy == args[0];
                    return "BackScreenStudioAnimationObserver";
                }
                if (method.getName().equals("onBackScreenAnimationResult") && args != null && args.length == 2) {
                    try {
                        AnimationInfo info = readInfo(args[0]);
                        if (info != null) observer.onResult(info, Boolean.TRUE.equals(args[1]));
                    } catch (Exception e) { Log.w(TAG, "Cannot read animation result", e); }
                }
                return null;
            });
            register.setAccessible(true); register.invoke(mManager, executor, animationListener);
            return true;
        } catch (Exception e) {
            animationListener = null; Log.w(TAG, "Animation listener unavailable", e); return false;
        }
    }
    public void unregisterAnimationObserver() {
        if (animationListener == null || mManager == null) return;
        try {
            Method unregister = findMethod(mManager.getClass(), "unregisterBackScreenAnimationListener", 1);
            if (unregister != null) { unregister.setAccessible(true); unregister.invoke(mManager, animationListener); }
        } catch (Exception e) { Log.w(TAG, "Animation listener unregister failed", e); }
        animationListener = null;
    }

    private BackScreenApi(Context ctx) {
        String err = "";
        boolean ok = false;
        String det = "";
        // 路径 1: Context.getSystemService("display_ext")
        try {
            Object svc = ctx.getSystemService(SERVICE_NAME);
            if (svc != null && svc.getClass().getName().contains("StepOSDisplayManager")) {
                mManager = svc;
                ok = true;
                det = "client";
            }
        } catch (Throwable t) {
            err += "gss:" + t.getClass().getSimpleName() + "; ";
        }
        if (!ok) {
            // 路径 2: ServiceManager.getService -> Stub.asInterface
            try {
                Class<?> sm = Class.forName("android.os.ServiceManager");
                IBinder binder = (IBinder) sm.getMethod("getService", String.class)
                        .invoke(null, SERVICE_NAME);
                if (binder == null) throw new IllegalStateException("binder null");
                Class<?> stub = Class.forName(
                        "android.hardware.display.IStepOSDisplayManager$Stub");
                mProxy = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
                ok = true;
                det = "binder";
            } catch (Throwable t) {
                err += "sm:" + t;
            }
        }
        available = ok;
        detail = ok ? det : err;
    }

    public static synchronized BackScreenApi get(Context ctx) {
        if (sInstance == null) {
            sInstance = new BackScreenApi(ctx.getApplicationContext());
        }
        return sInstance;
    }

    // ---------- 底层调用 ----------

    private Object call(String method, Object... args) throws Exception {
        Object target;
        Class<?>[] sig;
        if (mProxy != null) {
            target = mProxy;
            sig = toSig(args);
            Method m = findMethod(mProxy.getClass(), method, args.length);
            if (m == null) m = findMethod(Class.forName(
                    "android.hardware.display.IStepOSDisplayManager"), method, args.length);
            if (m == null) throw new NoSuchMethodException(method + "/" + args.length);
            m.setAccessible(true);
            return m.invoke(target, args);
        }
        if (mManager != null) {
            // framework 客户端类: setBackScreenAgentState(String) 单参包装
            Method m = findMethod(mManager.getClass(), method, args.length);
            if (m != null) { m.setAccessible(true); return m.invoke(mManager, args); }
        }
        throw new NoSuchMethodException(method);
    }

    private static Class<?>[] toSig(Object[] args) {
        Class<?>[] sig = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) sig[i] = args[i].getClass();
        return sig;
    }

    private static Method findMethod(Class<?> cls, String name, int argc) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == argc) {
                    try { m.setAccessible(true); } catch (Throwable ignored) {}
                    return m;
                }
            }
        }
        // 接口方法
        for (Class<?> ifc : cls.getInterfaces()) {
            Method m = findMethod(ifc, name, argc);
            if (m != null) return m;
        }
        return null;
    }

    private boolean tryCall(String method, Object... args) {
        try { call(method, args); return true; }
        catch (Throwable t) {
            Log.w(TAG, method + " failed: " + t);
            return false;
        }
    }

    // ---------- Agent 像素状态 (系统内置表情动画) ----------

    /** 状态: idle, wake_voice, wake_ai_key_click, wake_headset, listening, thinking,
     *  executing, speaking, success, error, pending, task_wait, task_running,
     *  task_success, task_error, idle_loafing, idle_night, awaiting_user ... */
    public boolean setAgentState(String state) {
        if (mProxy != null) return tryCall("setBackScreenAgentState", state, new Bundle());
        return tryCall("setBackScreenAgentState", state);
    }

    /** 复位 agent 状态, 释放 AGENT 临时层让位给 BASIC 文字/时钟层 */
    public boolean resetAgent() { return setAgentState("idle"); }

    // ---------- 自定义帧 (像素画) ----------

    /** 推送一帧自定义画面, frame 长度必须 391 (圆角死区自动熄灭) */
    public boolean setFrame(int[] frame) {
        if (frame == null || frame.length != N) return false;
        int[] f = Arrays.copyOf(frame, N);
        for (int i = 0; i < N; i++) if (!DOT_OK[i]) f[i] = 0;
        return tryCall("setBackScreenAgentFrame", f, new Bundle());
    }

    /** 清除自定义帧，恢复系统动画 */
    public boolean clearFrame() {
        boolean ok = tryCall("clearBackScreenAgentFrame");
        if (!ok) ok = tryCall("clearBackScreenAgentBitmap");
        return ok;
    }

    // ---------- 文字层 ----------

    public boolean setText(String text) { return tryCall("setBackScreenBasicText", text); }
    public boolean setTextType()       { return tryCall("setBackScreenBasicType", "text"); }
    public boolean setTimeType()       { return tryCall("setBackScreenBasicType", "time"); }
    public boolean setAgentType()      { return tryCall("setBackScreenBasicType", "agent"); }
    public boolean setInvert(boolean b){ return tryCall("setBackScreenBasicTextInvert", b); }
    public boolean setMarquee(boolean b){ return tryCall("setBackScreenBasicTextMarquee", b); }

    // ---------- 显示策略 ----------

    public boolean setEnabled(boolean on)       { return tryCall("setBackScreenEnabled", on); }
    /** 0=auto 1=always 2=custom 3=flip */
    public boolean setDisplayMode(int mode)     { return tryCall("setBackScreenDisplayMode", mode); }
    /** 亮度 0..255 */
    public boolean setBrightness(int v)         { return tryCall("setBackScreenBrightness", v); }
    /** 自动亮度 true/false */
    public boolean setBrightnessMode(boolean auto) {
        return tryCall("setBackScreenBrightnessMode", auto);
    }

    // ---------- 兜底: Settings 直写 (WRITE_SETTINGS 已授权时) ----------

    public static boolean putSetting(Context ctx, String key, String value) {
        try {
            android.net.Uri uri = android.provider.Settings.System.getUriFor(key);
            android.content.ContentResolver cr = ctx.getContentResolver();
            boolean ok = android.provider.Settings.System.putString(cr, key, value);
            if (ok && uri != null) cr.notifyChange(uri, null);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "putSetting " + key + ": " + t);
            return false;
        }
    }

    /** 文字兜底: 直接写 Settings.System 键（实测有效） */
    public boolean setTextViaSettings(Context ctx, String text) {
        boolean ok = putSetting(ctx, "stepos_back_screen_basic_text", text);
        ok &= putSetting(ctx, "stepos_back_screen_basic_type", "text");
        return ok;
    }
}
