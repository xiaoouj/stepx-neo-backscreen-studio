package com.workbuddy.backscreen;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.util.*;

/** Owns rear-display output independently of the activity. Historical class name retained. */
public class ChargeService extends Service {
    private static final String TAG = "BackScreenOutput", CHANNEL = "rear_display", STOP = "com.workbuddy.backscreen.STOP";
    private static final String NONE = "none", BATTERY = "battery", FRAMES = "frames", STATE = "state";
    private static final int NOTIFICATION = 1723;
    public static volatile boolean running;
    public static volatile int mode = 1, style, frameIndex;
    private static ChargeService instance;
    private static boolean previewActive;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BackScreenApi api;
    private BroadcastReceiver receiver;
    private PowerManager.WakeLock wakeLock;
    private String kind = NONE, state = "idle", label = "";
    private List<int[]> frames = Collections.emptyList();
    private int phase, nextFrame;
    private int[] lastFrame;
    private long lastSentAt, framesSent;
    private boolean stateSent;
    private HandoffPolicy handoff = new HandoffPolicy();
    private boolean animationObserverRegistered, releasedForSystem;
    private long animationResults;

    private String ownAgentState() { return STATE.equals(kind) ? state : null; }
    private void invalidateOutput() { lastFrame = null; stateSent = false; }

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("MainActivity", MODE_PRIVATE); }
    private static String savedKind(Context context) { return prefs(context).getString("output_kind", NONE); }
    public static boolean hasSavedOutput(Context context) { return !NONE.equals(savedKind(context)); }
    public static boolean isBatteryEnabled(Context context) { return BATTERY.equals(savedKind(context)); }
    public static boolean isAnimation(Context context) {
        return FRAMES.equals(savedKind(context)) && prefs(context).getInt("output_frames", 0) > 1;
    }
    public static String outputLabel(Context context) {
        String kind = savedKind(context);
        return BATTERY.equals(kind) ? "自动电量" : FRAMES.equals(kind)
                ? (isAnimation(context) ? "像素动画" : "像素画") : STATE.equals(kind) ? "系统表情" : "未开启";
    }
    private static AtomicFile archive(Context context) { return new AtomicFile(new File(context.getFilesDir(), "rear-display.frames")); }
    static FrameArchive loadFrames(Context context) throws IOException {
        try (InputStream in = new BufferedInputStream(archive(context).openRead())) { return FrameArchive.read(in); }
    }
    public static boolean displayFrames(Context context, List<int[]> sequence, int start) {
        AtomicFile file = archive(context); FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            FrameArchive.write(new BufferedOutputStream(stream), sequence, start);
            file.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) file.failWrite(stream);
            Log.e(TAG, "Cannot save frame sequence", e); return false;
        }
        String old = savedKind(context);
        if (!prefs(context).edit().putString("output_kind", FRAMES).putInt("output_frames", sequence.size()).commit()) return false;
        if (launch(context)) return true;
        prefs(context).edit().putString("output_kind", old).commit(); return false;
    }
    public static boolean displayState(Context context, String state, String label) {
        String old = savedKind(context);
        if (!prefs(context).edit().putString("output_kind", STATE).putString("output_state", state).putString("output_label", label).commit()) return false;
        if (launch(context)) return true;
        prefs(context).edit().putString("output_kind", old).commit(); return false;
    }
    public static boolean startBattery(Context context) {
        String old = savedKind(context);
        if (!prefs(context).edit().putString("output_kind", BATTERY).commit()) return false;
        if (launch(context)) return true;
        prefs(context).edit().putString("output_kind", old).commit(); return false;
    }
    public static boolean resumeSaved(Context context) { return hasSavedOutput(context) && launch(context); }
    private static boolean launch(Context context) {
        try { context.startForegroundService(new Intent(context, ChargeService.class)); return true; }
        catch (RuntimeException e) { Log.e(TAG, "Cannot start display service", e); return false; }
    }
    public static void setPreviewActive(boolean active) {
        previewActive = active;
        if (instance != null) {
            instance.lastFrame = null; instance.stateSent = false;
            if (active) instance.holdCpu(false);
            instance.handler.removeCallbacks(instance.tick);
            if (running) instance.handler.post(instance.tick);
        }
    }
    public static void releaseForManual(Context context) { stopOutput(context, false); }
    public static void stopOutput(Context context, boolean clear) {
        prefs(context).edit().putString("output_kind", NONE).commit();
        running = false;
        if (instance != null) { instance.handler.removeCallbacks(instance.tick); instance.holdCpu(false); }
        if (clear) {
            BackScreenApi api = BackScreenApi.get(context); api.clearFrame(); api.resetAgent();
        }
        context.stopService(new Intent(context, ChargeService.class));
    }

    private void holdCpu(boolean hold) {
        if (wakeLock == null) {
            PowerManager manager = (PowerManager)getSystemService(POWER_SERVICE);
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackScreenStudio:ActiveAnimation");
            wakeLock.setReferenceCounted(false);
        }
        if (hold && !wakeLock.isHeld()) wakeLock.acquire();
        else if (!hold && wakeLock.isHeld()) wakeLock.release();
    }
    private void send(int[] frame, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if ((force || !Arrays.equals(lastFrame, frame) || now-lastSentAt >= 30000) && api.setFrame(frame)) {
            lastFrame = frame; lastSentAt = now; framesSent++;
        }
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || instance != ChargeService.this) return;
            long delay = 1000;
            if (!previewActive) {
                BackScreenApi.AnimationInfo info = api.animationInfo();
                handoff.observe(info == null ? null : info.type, info == null ? null : info.state,
                        ownAgentState(), SystemClock.elapsedRealtime());
                if (handoff.shouldYield(SystemClock.elapsedRealtime())) {
                    holdCpu(false);
                    // Only remove our pixel override, never reset the assistant's agent state.
                    if (!releasedForSystem && info != null && "custom".equals(info.type)
                            && (FRAMES.equals(kind) || BATTERY.equals(kind))) api.clearFrame();
                    releasedForSystem = true;
                    handler.postDelayed(this, 300); return;
                }
                if (handoff.takeInvalidation()) invalidateOutput();
                releasedForSystem = false;
                if (FRAMES.equals(kind)) {
                    boolean animation = frames.size() > 1; holdCpu(animation);
                    frameIndex = nextFrame; send(frames.get(nextFrame), animation);
                    if (animation) { nextFrame = (nextFrame+1)%frames.size(); delay = 250; }
                } else if (STATE.equals(kind)) {
                    holdCpu(false);
                    if (!stateSent) stateSent = api.setAgentState(state);
                } else if (BATTERY.equals(kind)) {
                    int[] battery = readBattery(ChargeService.this); boolean charging = battery[1] == 1;
                    boolean display = mode == 0 || charging || (mode == 2 && battery[0] == 100);
                    boolean animate = display && charging && battery[0] < 100;
                    holdCpu(animate);
                    if (display) send(ChargeArt.frame(battery[0], charging, phase++, style), false);
                    else if (lastFrame == null || lastFrame.length != 0) {
                        api.clearFrame(); api.resetAgent(); lastFrame = new int[0];
                    }
                    delay = animate ? 250 : 1000;
                }
            }
            handler.postDelayed(this, delay);
        }
    };

    @Override public void onCreate() {
        super.onCreate(); instance = this; api = BackScreenApi.get(this);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "背屏持续显示", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("像素画、动画与电量在后台持续显示时使用"); channel.setShowBadge(false); channel.enableVibration(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (running && BATTERY.equals(kind)) { handler.removeCallbacks(tick); handler.post(tick); }
            }
        };
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        animationObserverRegistered = api.registerAnimationObserver(command -> handler.post(command), (info, success) -> {
            animationResults++;
            // The boolean is a result, not a start/end flag. Re-query actual playback.
            if (HandoffPolicy.isExternalResult(info.type, info.state, ownAgentState())) {
                handoff.hint(); invalidateOutput();
                if (running) { handler.removeCallbacks(tick); handler.postDelayed(tick, 80); }
            }
        });
    }
    private Notification notification() {
        int tab = FRAMES.equals(kind) ? 2 : BATTERY.equals(kind) ? 3 : 0;
        Intent open = new Intent(this, MainActivity.class).putExtra("show_tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ChargeService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String detail = STATE.equals(kind) ? "系统表情 · " + label : outputLabel(this) + " · 后台持续显示";
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_status)
                .setContentTitle("背屏显示运行中").setContentText(detail).setContentIntent(content)
                .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "停止显示", stop).build());
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopOutput(this, true); return START_NOT_STICKY; }
        kind = savedKind(this);
        if (NONE.equals(kind)) { stopSelf(); return START_NOT_STICKY; }
        try {
            // Promote before any disk I/O. specialUse describes this physical-display controller.
            Notification note = notification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION, note);
            mode = Math.max(0, Math.min(2, prefs(this).getInt("charge_mode", 1)));
            style = Math.max(0, Math.min(ChargeArt.STYLE_COUNT-1, prefs(this).getInt("charge_style", 0)));
            if (FRAMES.equals(kind)) { FrameArchive saved = loadFrames(this); frames = saved.frames; nextFrame = saved.start; frameIndex = nextFrame; }
            else frames = Collections.emptyList();
            state = prefs(this).getString("output_state", "idle"); label = prefs(this).getString("output_label", "");
            running = true; lastFrame = null; stateSent = false; handoff = new HandoffPolicy(); releasedForSystem = false;
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION, notification());
            handler.removeCallbacks(tick); handler.post(tick); return START_STICKY;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Cannot restore rear display", e); running = false; holdCpu(false);
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY;
        }
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(tick); holdCpu(false);
        api.unregisterAnimationObserver();
        try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
        if (instance == this) { running = false; instance = null; }
        // Keep the snapshot and desired mode for a system-managed sticky restart.
        super.onDestroy();
    }
    @Override protected void dump(FileDescriptor fd, PrintWriter out, String[] args) {
        out.println("kind="+kind+" running="+running+" desired="+savedKind(this));
        out.println("frameCount="+frames.size()+" frameIndex="+frameIndex+" framesSent="+framesSent);
        out.println("lastSendElapsed="+lastSentAt+" wakeLock="+(wakeLock != null && wakeLock.isHeld())+" preview="+previewActive);
        out.println("batteryMode="+mode+" batteryStyle="+style);
        out.println("animationObserver="+animationObserverRegistered+" results="+animationResults+" "+handoff.describe());
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    public static int[] readBattery(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = 0, charging = 0;
        if (battery != null) {
            int raw = battery.getIntExtra("level", 0), scale = battery.getIntExtra("scale", 100);
            level = scale > 0 ? Math.max(0, Math.min(100, (int)(raw * 100L / scale))) : 0;
            int status = battery.getIntExtra("status", 0);
            charging = battery.getIntExtra("plugged", 0) != 0 || status == 2 || status == 5 ? 1 : 0;
        }
        return new int[]{level, charging};
    }
}
