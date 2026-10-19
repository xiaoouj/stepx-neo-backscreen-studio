package com.workbuddy.backscreen;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.List;

/** Native, dependency-free studio for the 17 × 23 rear display. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF101513, PANEL = 0xFF1A211E, TILE = 0xFF242E28;
    private static final int INK = 0xFFF2F4EB, MUTED = 0xFF96A298, ACCENT = 0xFFD1F3AB;
    private static final int LINE = 0xFF303C33, RED = 0xFFFFB4A7;
    private static final String[] TAB_NAMES = {"表情", "文字", "像素", "电量", "设置"};
    private static final String[][] STATES = {
        {"idle", "空闲"}, {"idle_loafing", "摸鱼"}, {"idle_night", "睡觉"},
        {"listening", "聆听"}, {"thinking", "思考"}, {"executing", "执行中"},
        {"speaking", "说话"}, {"success", "成功"}, {"error", "失败"},
        {"awaiting_user", "待接管"}, {"task_running", "任务中"}, {"task_wait", "任务等待"},
        {"task_success", "任务成功"}, {"task_error", "任务失败"},
        {"wake_voice", "语音唤醒"}, {"wake_ai_key_click", "AI 键唤醒"}, {"pending", "挂起"}
    };
    private static final String[] MODES = {"实时电量", "仅充电时", "充满保留"};
    private static final String[] STYLES = {"大数字", "电池", "环形"};
    private static final String[] STYLE_DETAILS = {"大号百分比\n远看也清晰", "经典电池轮廓\n由下向上填充", "顺时针刻度\n中心显示电量"};
    private static final String[] MODE_HELP = {
        "持续显示当前电量，插电时轻轻呼吸。",
        "接通电源时显示，拔下后恢复系统画面。",
        "充电时显示；达到 100% 后保留，电量下降即恢复系统画面。"
    };
    private BackScreenApi api;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ViewFlipper pages;
    private Button[] tabs;
    private TextView connection;
    private int currentTab;
    private PixelEditorView editor;
    private TextView frameLabel, modeHelp, batteryValue, batteryState, batteryService, batteryDetails;
    private Button playButton, eraseButton, onionButton, previewButton;
    private Button[] brushes, modeButtons, styleButtons;
    private Switch autoSwitch, alwaysSwitch, brightnessSwitch;
    private SeekBar brightness;
    private EditText textInput;
    private boolean marquee = true, inverted, playing, previewing, resumed, syncingAuto;
    private int playIndex, phase, chargeMode = 1, chargeStyle;
    private ChargePreviewView chargePreview;
    private HandoffPolicy previewHandoff = new HandoffPolicy();
    private boolean previewYielding;

    private final Runnable playLoop = new Runnable() {
        @Override public void run() {
            if (!playing || !resumed) return;
            if (!ChargeService.isAnimation(MainActivity.this)) { stopPlayUi(); return; }
            int index = ChargeService.frameIndex;
            if (index >= 0 && index < editor.frames.size() && editor.current != index) {
                editor.setCurrent(index); updateFrameLabel();
            }
            ui.postDelayed(this, 250);
        }
    };
    private final Runnable chargeLoop = new Runnable() {
        @Override public void run() {
            if (!resumed || (currentTab != 3 && !previewing)) return;
            int[] state = ChargeService.readBattery(MainActivity.this);
            int[] frame = ChargeArt.frame(state[0], state[1] == 1, phase++, chargeStyle);
            chargePreview.setFrame(frame);
            String description = "背屏预览，电量 " + state[0] + "%";
            if (!description.contentEquals(chargePreview.getContentDescription() == null ? "" : chargePreview.getContentDescription()))
                chargePreview.setContentDescription(description);
            if (previewing) {
                BackScreenApi.AnimationInfo current = api.animationInfo();
                long now = android.os.SystemClock.elapsedRealtime();
                previewHandoff.observe(current == null ? null : current.type, current == null ? null : current.state, null, now);
                boolean yield = previewHandoff.shouldYield(now);
                if (yield && !previewYielding && current != null && "custom".equals(current.type)) api.clearFrame();
                previewYielding = yield;
                if (!yield) api.setFrame(frame);
            }
            updateLabel(batteryValue, state[0] + "%");
            updateLabel(batteryState, state[0] == 100 ? "电量已满" : state[1] == 1 ? "正在充电" : "使用电池中");
            boolean batteryOn = ChargeService.isBatteryEnabled(MainActivity.this);
            updateLabel(batteryService, batteryOn ? (ChargeService.running ? "● 自动显示中 · " + MODES[ChargeService.mode] : "等待恢复电量显示")
                    : ChargeService.running ? "背屏正在显示" + ChargeService.outputLabel(MainActivity.this) : "仅在 App 内预览");
            syncingAuto = true; autoSwitch.setChecked(batteryOn); syncingAuto = false;
            ui.postDelayed(this, 250);
        }
    };
    private final Runnable endPreview = () -> finishPreview(true);

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        api = BackScreenApi.get(this);
        marquee = !"0".equals(Settings.System.getString(getContentResolver(), "stepos_back_screen_basic_text_marquee"));
        inverted = "1".equals(Settings.System.getString(getContentResolver(), "stepos_back_screen_basic_text_invert"));
        chargeMode = Math.max(0, Math.min(2, getPreferences(0).getInt("charge_mode", 1)));
        chargeStyle = Math.max(0, Math.min(ChargeArt.STYLE_COUNT - 1, getPreferences(0).getInt("charge_style", 0)));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(12), dp(22), dp(15));
        LinearLayout brand = column();
        TextView overline = label("BACKSCREEN  /  STUDIO", 10, MUTED);
        overline.setLetterSpacing(.17f); brand.addView(overline);
        TextView title = label("背屏工坊", 26, INK); bold(title);
        brand.addView(title, gap(-2, 4));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        connection = label("", 11, ACCENT); connection.setPadding(dp(10), dp(7), dp(10), dp(7));
        connection.setBackground(surface(TILE, 20, LINE)); header.addView(connection);
        root.addView(header, full(-2));
        pages = new ViewFlipper(this);
        pages.addView(buildStates()); pages.addView(buildText()); pages.addView(buildPixel());
        pages.addView(buildCharge()); pages.addView(buildSettings());
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        View divider = new View(this); divider.setBackgroundColor(LINE); root.addView(divider, full(dp(1)));
        LinearLayout nav = row(); nav.setPadding(dp(12), dp(7), dp(12), dp(7));
        tabs = new Button[5];
        for (int i = 0; i < 5; i++) {
            final int tab = i;
            Button b = button(TAB_NAMES[i], BG, MUTED); b.setTextSize(10);
            b.setCompoundDrawablePadding(dp(5)); b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(6), 0, dp(6));
            b.setOnClickListener(v -> showTab(tab)); tabs[i] = b;
            nav.addView(b, weighted(60));
        }
        root.addView(nav, full(-2));
        setContentView(root);
        refreshConnection();
        showTab(getIntent().hasExtra("show_tab") ? getIntent().getIntExtra("show_tab", 0)
                : saved == null ? Math.max(0, Math.min(4, getPreferences(0).getInt("tab", 0))) : saved.getInt("tab", 0));
    }

    private void showTab(int tab) {
        currentTab = Math.max(0, Math.min(4, tab)); pages.setDisplayedChild(currentTab);
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = currentTab == i;
            tabs[i].setTextColor(selected ? ACCENT : MUTED);
            tabs[i].setCompoundDrawables(null, new StudioIcon(i, selected ? ACCENT : MUTED, dp(22)), null, null);
            tabs[i].setBackground(surface(selected ? TILE : BG, 16, 0));
        }
        getPreferences(0).edit().putInt("tab", currentTab).apply();
        if (tab != 1) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pages.getWindowToken(), 0);
        ui.removeCallbacks(chargeLoop);
        if (resumed && (currentTab == 3 || previewing)) ui.post(chargeLoop);
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; refreshConnection();
        if (ChargeService.hasSavedOutput(this) && !ChargeService.running && !ChargeService.resumeSaved(this))
            toast("后台显示暂未恢复，请重新推送");
        playing = ChargeService.isAnimation(this);
        if (playButton != null) playButton.setText(playing ? "停止" : "播放");
        ui.removeCallbacks(playLoop); if (playing) ui.post(playLoop);
        if (currentTab == 3) { ui.removeCallbacks(chargeLoop); ui.post(chargeLoop); }
    }
    @Override protected void onPause() {
        resumed = false; ui.removeCallbacks(playLoop); finishPreview(true); ui.removeCallbacks(chargeLoop);
        super.onPause();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.hasExtra("show_tab")) showTab(intent.getIntExtra("show_tab", 0));
    }
    private void requestDisplayNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notification_requested", false)) {
            getPreferences(0).edit().putBoolean("notification_requested", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 73);
        }
    }
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", currentTab); super.onSaveInstanceState(out);
    }
    private void refreshConnection() {
        connection.setText(api.available ? "● 已连接" : "○ 未连接");
        connection.setTextColor(api.available ? ACCENT : RED);
    }
    private void ensureApi() {
        if (api == null || !api.available) api = BackScreenApi.get(this);
        refreshConnection();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private void updateLabel(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    /** A single manual action owns the display until automatic mode is enabled again. */
    private void manualDisplay() {
        stopPlayUi(); finishPreview(false);
        ChargeService.releaseForManual(this);
        if (autoSwitch != null) { syncingAuto = true; autoSwitch.setChecked(false); syncingAuto = false; }
        ensureApi();
    }

    private View buildStates() {
        LinearLayout p = page("给背面，一点个性。", "表情、文字与像素，让小小背屏有自己的语言。");
        LinearLayout hero = row(); hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(surface(PANEL, 24, LINE)); hero.setPadding(dp(18), dp(12), dp(14), dp(12));
        LinearLayout copy = column();
        TextView tag = label("AMOO COLLECTION", 10, ACCENT); tag.setLetterSpacing(.12f); copy.addView(tag);
        TextView head = label("今天，什么心情？", 20, INK); bold(head); copy.addView(head, gap(-2, 10));
        copy.addView(label("轻点下方表情，即刻切换", 11, MUTED), gap(-2, 6));
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ChargePreviewView smile = new ChargePreviewView(this); smile.setFrame(PixelArt.get("smiley").get(0));
        hero.addView(smile, new LinearLayout.LayoutParams(dp(86), dp(116)));
        p.addView(hero, gap(-2, 18));
        section(p, "系统表情", "17 种状态");
        for (int start = 0; start < STATES.length; start += 3) {
            LinearLayout r = row();
            for (int j = start; j < start + 3; j++) {
                if (j >= STATES.length) { r.addView(new View(this), weighted(48)); continue; }
                final int index = j;
                Button b = button(STATES[j][1], PANEL, INK);
                b.setOnClickListener(v -> {
                    manualDisplay(); api.clearFrame();
                    boolean ok = ChargeService.displayState(this, STATES[index][0], STATES[index][1]);
                    if (ok) requestDisplayNotifications();
                    toast(ok ? "已切换 · " + STATES[index][1] : "背屏服务暂不可用");
                });
                r.addView(b, weighted(48));
            }
            p.addView(r, gap(-2, 7));
        }
        section(p, "像素动画", "点击播放");
        String[][] art = {{"心跳", "heart_beat"}, {"笑脸", "smiley"}, {"星光", "star_blink"},
                {"加载", "loading"}, {"音符", "note"}, {"黑桃", "spade"}};
        for (int start = 0; start < art.length; start += 3) {
            LinearLayout r = row();
            for (int j = start; j < start + 3; j++) {
                final String key = art[j][1];
                LinearLayout tile = column(); tile.setGravity(Gravity.CENTER); tile.setPadding(0, dp(10), 0, dp(10));
                tile.setBackground(surface(PANEL, 18, LINE)); tile.setContentDescription(art[j][0] + "像素动画");
                ChargePreviewView pv = new ChargePreviewView(this); pv.setFrame(PixelArt.get(key).get(0));
                tile.addView(pv, new LinearLayout.LayoutParams(dp(48), dp(65)));
                TextView name = label(art[j][0], 12, INK); name.setGravity(Gravity.CENTER); tile.addView(name, gap(-1, 5));
                tile.setOnClickListener(v -> playPreset(key)); r.addView(tile, weighted(-2));
            }
            p.addView(r, gap(-2, 8));
        }
        Button reset = secondary("停止播放 · 恢复系统");
        reset.setOnClickListener(v -> { manualDisplay(); api.clearFrame(); api.resetAgent(); toast("已恢复系统画面"); });
        p.addView(reset, gap(dp(48), 16)); return scroll(p);
    }
    private void playPreset(String key) {
        manualDisplay(); List<int[]> frames = PixelArt.get(key);
        if (frames.isEmpty()) return;
        editor.frames.clear(); for (int[] frame : frames) editor.frames.add(frame.clone());
        editor.setCurrent(0); updateFrameLabel(); startPlay();
    }

    private View buildText() {
        LinearLayout p = page("把想说的，留在背面。", "一句问候，一个昵称，或只有你看得懂的暗号。");
        LinearLayout card = card();
        card.addView(label("YOUR MESSAGE", 10, ACCENT));
        textInput = new EditText(this); textInput.setTextSize(26); textInput.setTextColor(INK);
        textInput.setHintTextColor(0xFF68766A); textInput.setHint("HELLO, AMOO");
        textInput.setSingleLine(true); textInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        textInput.setPadding(0, dp(14), 0, dp(14)); textInput.setBackgroundColor(Color.TRANSPARENT);
        card.addView(textInput, full(dp(80)));
        card.addView(label("最多 20 字符 · 长文字可开启滚动显示", 11, MUTED));
        p.addView(card, gap(-2, 20));
        LinearLayout options = card();
        Switch marqueeSwitch = toggle("滚动文字", marquee);
        marqueeSwitch.setOnCheckedChangeListener((v, checked) -> { marquee = checked; ensureApi();
            if (!api.setMarquee(checked)) BackScreenApi.putSetting(this, "stepos_back_screen_basic_text_marquee", checked ? "1" : "0"); });
        options.addView(marqueeSwitch, full(dp(48)));
        Switch invertSwitch = toggle("反色显示", inverted);
        invertSwitch.setOnCheckedChangeListener((v, checked) -> { inverted = checked; ensureApi();
            if (!api.setInvert(checked)) BackScreenApi.putSetting(this, "stepos_back_screen_basic_text_invert", checked ? "1" : "0"); });
        options.addView(invertSwitch, full(dp(48))); p.addView(options, gap(-2, 12));
        Button send = primary("发送到背屏  ↗"); send.setOnClickListener(v -> sendText());
        p.addView(send, gap(dp(52), 14));
        section(p, "快捷短语", "点击即发送");
        String[] phrases = {"HI!", "HELLO", "AMOO", "STEPX", "MAKER", "01:23"};
        for (int start = 0; start < phrases.length; start += 3) {
            LinearLayout r = row();
            for (int j = start; j < start + 3; j++) {
                final String phrase = phrases[j]; Button b = secondary(phrase);
                b.setOnClickListener(v -> { textInput.setText(phrase); sendText(); }); r.addView(b, weighted(44));
            }
            p.addView(r, gap(-2, 8));
        }
        section(p, "基础画面", "");
        LinearLayout modes = row();
        String[] names = {"时钟", "Amoo", "文字"};
        for (int i = 0; i < names.length; i++) {
            final int mode = i; Button b = secondary(names[i]);
            b.setOnClickListener(v -> { manualDisplay(); api.clearFrame(); api.resetAgent();
                boolean ok = mode == 0 ? api.setTimeType() : mode == 1 ? api.setAgentType() : api.setTextType();
                toast(ok ? "已切换基础画面" : "切换失败"); }); modes.addView(b, weighted(46));
        }
        p.addView(modes, gap(-2, 8));
        note(p, "文字不显示时，可将背屏朝上，或在设置中开启背屏常显。省电模式可能限制显示。");
        return scroll(p);
    }
    private void sendText() {
        String value = textInput.getText().toString().trim();
        if (value.isEmpty()) { textInput.setError("先写点什么吧"); return; }
        manualDisplay(); api.clearFrame(); api.resetAgent(); api.setTextType();
        api.setMarquee(marquee); api.setInvert(inverted);
        boolean ok = api.setText(value); if (!ok) ok = api.setTextViaSettings(this, value);
        toast(ok ? "文字已发送" : "发送失败，请检查背屏连接");
    }

    private View buildPixel() {
        LinearLayout p = page("一颗一颗，画出灵感。", "17 × 23 点阵画布 · 拖动画笔，长按擦除单点。");
        LinearLayout canvasCard = card(); canvasCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        editor = new PixelEditorView(this, null);
        try {
            FrameArchive savedFrames = ChargeService.loadFrames(this);
            editor.frames.clear(); editor.frames.addAll(savedFrames.frames); editor.setCurrent(savedFrames.start);
        } catch (java.io.IOException ignored) { }
        canvasCard.addView(editor, full(dp(330))); p.addView(canvasCard, gap(-2, 16));
        editor.setListener(this::updateFrameLabel);
        LinearLayout tools = row(); brushes = new Button[3];
        String[] names = {"高亮", "柔光", "微光"}; int[] levels = {255, 170, 85};
        for (int i = 0; i < 3; i++) {
            final int index = i; Button b = secondary(names[i]); brushes[i] = b;
            b.setOnClickListener(v -> { editor.brush = levels[index]; editor.eraseMode = false; updateTools(); });
            tools.addView(b, weighted(44));
        }
        eraseButton = secondary("橡皮"); eraseButton.setOnClickListener(v -> { editor.eraseMode = !editor.eraseMode; updateTools(); });
        tools.addView(eraseButton, weighted(44)); p.addView(tools, gap(-2, 12));
        LinearLayout actions = row();
        Button fill = secondary("填充"); fill.setOnClickListener(v -> editor.fillAll());
        Button clear = secondary("清空"); clear.setOnClickListener(v -> editor.clearFrame());
        onionButton = secondary("邻帧参考"); onionButton.setOnClickListener(v -> { editor.toggleOnion(); updateTools(); });
        actions.addView(fill, weighted(42)); actions.addView(clear, weighted(42)); actions.addView(onionButton, weighted(42));
        p.addView(actions, gap(-2, 7));
        section(p, "动画时间轴", "每帧 0.25 秒");
        LinearLayout timeline = row();
        Button prev = secondary("‹"); prev.setTextSize(25);
        prev.setOnClickListener(v -> { stopPlay(); editor.setCurrent((editor.current + editor.frames.size() - 1) % editor.frames.size()); updateFrameLabel(); });
        Button next = secondary("›"); next.setTextSize(25);
        next.setOnClickListener(v -> { stopPlay(); editor.setCurrent((editor.current + 1) % editor.frames.size()); updateFrameLabel(); });
        frameLabel = label("", 13, INK); frameLabel.setGravity(Gravity.CENTER);
        timeline.addView(prev, weighted(44)); timeline.addView(frameLabel, new LinearLayout.LayoutParams(0, dp(44), 2)); timeline.addView(next, weighted(44));
        p.addView(timeline, gap(-2, 8));
        LinearLayout frames = row();
        Button add = secondary("＋ 复制帧"); add.setOnClickListener(v -> { stopPlay(); editor.duplicateFrame(); updateFrameLabel(); });
        Button del = secondary("删除帧"); del.setOnClickListener(v -> { stopPlay(); editor.deleteFrame(); updateFrameLabel(); });
        playButton = secondary("播放"); playButton.setOnClickListener(v -> { if (playing) stopPlay(); else { manualDisplay(); startPlay(); } });
        frames.addView(add, weighted(44)); frames.addView(del, weighted(44)); frames.addView(playButton, weighted(44)); p.addView(frames, gap(-2, 7));
        LinearLayout output = row();
        Button send = primary("推送当前帧 ↗"); send.setOnClickListener(v -> {
            manualDisplay(); boolean ok = ChargeService.displayFrames(this, java.util.Collections.singletonList(editor.frame()), 0);
            if (ok) requestDisplayNotifications(); toast(ok ? "当前帧已推送，可退出 App 持续显示" : "推送失败");
        });
        Button image = secondary("导入图片"); image.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT); pick.setType("image/*"); startActivityForResult(pick, 42); });
        output.addView(send, new LinearLayout.LayoutParams(0, dp(50), 1.5f)); output.addView(image, weighted(50)); p.addView(output, gap(-2, 14));
        note(p, "推送后可退出 App，画面由后台服务持续显示。动画持续运行会增加耗电，可在常驻通知中停止。");
        updateFrameLabel(); updateTools(); return scroll(p);
    }
    private void updateTools() {
        if (brushes == null || eraseButton == null || onionButton == null) return;
        int[] levels = {255, 170, 85};
        for (int i = 0; i < 3; i++) selected(brushes[i], !editor.eraseMode && editor.brush == levels[i]);
        selected(eraseButton, editor.eraseMode); selected(onionButton, editor.onion);
    }
    private void updateFrameLabel() {
        if (frameLabel != null) frameLabel.setText(String.format(java.util.Locale.ROOT, "%02d  /  %02d", editor.current + 1, editor.frames.size()));
    }
    private void startPlay() {
        if (!ChargeService.displayFrames(this, editor.frames, editor.current)) { toast("无法保存并启动动画"); return; }
        requestDisplayNotifications(); playing = editor.frames.size() > 1; playButton.setText(playing ? "停止" : "播放");
        ui.removeCallbacks(playLoop); if (resumed && playing) ui.post(playLoop);
        toast(playing ? "动画已启动，退出 App 后继续显示" : "画面已保存，退出 App 后继续显示");
    }
    private void stopPlayUi() { playing = false; ui.removeCallbacks(playLoop); if (playButton != null) playButton.setText("播放"); }
    private void stopPlay() {
        stopPlayUi(); if (ChargeService.isAnimation(this)) ChargeService.stopOutput(this, true);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 42 || result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            // Decode near the LED resolution rather than allocating a full camera image.
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) { android.graphics.BitmapFactory.decodeStream(stream, null, options); }
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            android.graphics.Bitmap bitmap;
            try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) { bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options); }
            if (bitmap == null) throw new java.io.IOException();
            int[] frame = PixelEditorView.fromBitmap(bitmap, false); bitmap.recycle();
            stopPlay(); editor.frames.add(frame); editor.setCurrent(editor.frames.size() - 1); updateFrameLabel();
            toast("已导入画布，点击推送即可显示");
        } catch (Exception e) { toast("无法读取这张图片，请换一张试试"); }
    }

    private View buildCharge() {
        LinearLayout p = page("电量，一眼就懂。", "大数字，轻呼吸。为这块小屏重新设计。");
        LinearLayout hero = card();
        LinearLayout top = row();
        TextView tag = label("LIVE BATTERY", 10, ACCENT); tag.setLetterSpacing(.14f);
        top.addView(tag, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(label("17 × 23", 10, MUTED)); hero.addView(top);
        LinearLayout display = row(); display.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout shell = column(); shell.setBackground(surface(0xFF0D120F, 23, LINE));
        chargePreview = new ChargePreviewView(this); shell.addView(chargePreview, full(dp(192)));
        display.addView(shell, new LinearLayout.LayoutParams(dp(136), dp(192)));
        LinearLayout values = column(); values.setPadding(dp(20), 0, 0, 0);
        values.addView(label("当前电量", 11, MUTED));
        batteryValue = label("—", 40, INK); bold(batteryValue); batteryValue.setLetterSpacing(-.05f); values.addView(batteryValue, gap(-2, 6));
        batteryState = label("读取中", 12, ACCENT); values.addView(batteryState, gap(-2, 4));
        batteryDetails = label("", 11, MUTED); batteryDetails.setLineSpacing(dp(4), 1); values.addView(batteryDetails, gap(-2, 20));
        display.addView(values, new LinearLayout.LayoutParams(0, -2, 1)); hero.addView(display, gap(-2, 18));
        batteryService = label("", 11, MUTED); hero.addView(batteryService, gap(-2, 14));
        LinearLayout styles = row(); styleButtons = new Button[STYLES.length];
        for (int i = 0; i < STYLES.length; i++) {
            final int style = i; Button b = secondary(STYLES[i]);
            b.setOnClickListener(v -> {
                chargeStyle = style; ChargeService.style = style;
                getPreferences(0).edit().putInt("charge_style", style).apply(); refreshStyle();
                ui.removeCallbacks(chargeLoop); if (resumed) ui.post(chargeLoop);
            });
            styleButtons[i] = b; styles.addView(b, weighted(42));
        }
        hero.addView(styles, gap(-2, 16)); refreshStyle();
        p.addView(hero, gap(-2, 20));
        previewButton = primary("在背屏预览 12 秒  ↗");
        previewButton.setOnClickListener(v -> { if (previewing) finishPreview(true); else previewBattery(); });
        p.addView(previewButton, gap(dp(52), 14));
        LinearLayout control = card();
        autoSwitch = toggle("自动显示电量", ChargeService.isBatteryEnabled(this));
        autoSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (syncingAuto || checked == ChargeService.isBatteryEnabled(this)) return;
            finishPreview(false); stopPlayUi(); ensureApi();
            if (checked) {
                ChargeService.mode = chargeMode; ChargeService.style = chargeStyle;
                if (ChargeService.startBattery(this)) requestDisplayNotifications();
                else toast("无法启动后台电量显示");
            } else ChargeService.stopOutput(this, true);
        });
        control.addView(autoSwitch, full(dp(44)));
        control.addView(label("选择背屏何时显示电量", 11, MUTED), gap(-2, 2));
        LinearLayout modes = row(); modeButtons = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int index = i; Button b = secondary(MODES[i]); b.setTextSize(11);
            b.setOnClickListener(v -> {
                chargeMode = index; ChargeService.mode = index;
                getPreferences(0).edit().putInt("charge_mode", index).apply(); refreshMode();
            }); modeButtons[i] = b; modes.addView(b, weighted(42));
        }
        control.addView(modes, gap(-2, 16));
        modeHelp = label("", 11, MUTED); modeHelp.setLineSpacing(dp(3), 1); control.addView(modeHelp, gap(-2, 12));
        p.addView(control, gap(-2, 12)); refreshMode();
        note(p, "三种样式均显示真实电量，充电时柔和呼吸，100% 静止显示。预览时请将背屏朝上，或开启背屏常显。");
        return scroll(p);
    }
    private void refreshMode() {
        for (int i = 0; i < 3; i++) selected(modeButtons[i], chargeMode == i);
        modeHelp.setText(MODE_HELP[chargeMode]);
    }
    private void refreshStyle() {
        for (int i = 0; i < styleButtons.length; i++) selected(styleButtons[i], i == chargeStyle);
        batteryDetails.setText(STYLE_DETAILS[chargeStyle]);
    }
    private void previewBattery() {
        ensureApi(); if (!api.available) { toast("背屏尚未连接"); return; }
        previewHandoff = new HandoffPolicy(); previewYielding = false;
        previewing = true; ChargeService.setPreviewActive(true);
        previewButton.setText("结束预览 · 恢复显示"); ui.removeCallbacks(endPreview);
        ui.removeCallbacks(chargeLoop); ui.post(chargeLoop); ui.postDelayed(endPreview, 12000);
    }
    private void finishPreview(boolean restore) {
        ui.removeCallbacks(endPreview);
        if (!previewing) return;
        previewing = false; ChargeService.setPreviewActive(false);
        if (previewButton != null) previewButton.setText("在背屏预览 12 秒  ↗");
        if (restore && !ChargeService.running && api != null) { api.clearFrame(); api.resetAgent(); }
    }

    private View buildSettings() {
        LinearLayout p = page("小屏幕，也有自己的节奏。", "亮度与显示策略，按你的习惯调整。");
        section(p, "显示偏好", "");
        LinearLayout policy = card();
        alwaysSwitch = toggle("背屏常显", false);
        alwaysSwitch.setChecked("1".equals(Settings.System.getString(getContentResolver(), "stepos_back_screen_display_mode")));
        alwaysSwitch.setOnCheckedChangeListener((v, checked) -> { ensureApi();
            toast(api.setDisplayMode(checked ? 1 : 0) ? (checked ? "背屏常显已开启" : "已恢复自动显示") : "设置失败"); });
        policy.addView(alwaysSwitch, full(dp(48)));
        policy.addView(label("关闭后，按系统的翻转与抬起策略显示", 11, MUTED), gap(-2, 0));
        brightnessSwitch = toggle("自动亮度", true);
        brightnessSwitch.setOnCheckedChangeListener((v, checked) -> { ensureApi();
            if (api.setBrightnessMode(checked)) brightness.setEnabled(!checked); else toast("亮度设置失败"); });
        policy.addView(brightnessSwitch, gap(dp(48), 14));
        LinearLayout scale = row(); scale.addView(label("暗", 11, MUTED));
        brightness = new SeekBar(this); brightness.setMax(255); brightness.setProgress(127); brightness.setEnabled(false);
        brightness.setProgressTintList(ColorStateList.valueOf(ACCENT)); brightness.setThumbTintList(ColorStateList.valueOf(ACCENT));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { if (user) { ensureApi(); api.setBrightness(value); } }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        scale.setGravity(Gravity.CENTER_VERTICAL); scale.addView(brightness, new LinearLayout.LayoutParams(0, dp(40), 1)); scale.addView(label("亮", 11, MUTED));
        policy.addView(scale, gap(-2, 8)); p.addView(policy, gap(-2, 10));
        section(p, "后台持续显示", "");
        LinearLayout background = card();
        background.addView(label("像素画与动画可在退出 App 后继续显示", 12, INK));
        note(background, "推送的画面会保存；系统回收后尝试恢复。主动强行停止应用后，需要再次打开。保持常驻通知可随时停止显示。");
        Button batteryPolicy = secondary("允许后台运行  ↗");
        batteryPolicy.setOnClickListener(v -> {
            android.os.PowerManager power = (android.os.PowerManager)getSystemService(POWER_SERVICE);
            try {
                if (power.isIgnoringBatteryOptimizations(getPackageName())) {
                    toast("已允许后台运行"); return;
                }
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        });
        background.addView(batteryPolicy, gap(dp(46), 14)); p.addView(background, gap(-2, 10));
        section(p, "背屏工具", "");
        Button direction = secondary("测试显示方向  ↗");
        direction.setOnClickListener(v -> { manualDisplay(); int[] frame = new int[BackScreenApi.N];
            for (int y = 3; y < 10; y++) for (int x = 3; x < 13-y; x++) frame[y*BackScreenApi.W+x] = 255;
            toast(api.setFrame(frame) ? "三角形应出现在背屏左上方" : "发送失败"); });
        p.addView(direction, gap(dp(48), 8));
        Button reset = button("清除自定义 · 恢复系统", PANEL, RED);
        reset.setOnClickListener(v -> { manualDisplay(); api.clearFrame(); api.resetAgent(); toast("已恢复系统画面"); });
        p.addView(reset, gap(dp(48), 9));
        section(p, "设备与连接", "");
        LinearLayout info = card();
        infoRow(info, "背屏服务", api.available ? "已连接" : "未连接");
        infoRow(info, "物理点阵", "17 × 23");
        infoRow(info, "系统设置权限", Settings.System.canWrite(this) ? "已允许" : "未允许");
        TextView version = label("背屏工坊 1.3  /  " + api.detail, 10, MUTED); info.addView(version, gap(-2, 14));
        p.addView(info, gap(-2, 10)); return scroll(p);
    }

    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout page(String title, String subtitle) {
        LinearLayout p = column(); p.setPadding(dp(20), dp(12), dp(20), dp(24));
        TextView heading = label(title, 22, INK); bold(heading); p.addView(heading);
        TextView detail = label(subtitle, 12, MUTED); detail.setLineSpacing(dp(4), 1); p.addView(detail, gap(-2, 8)); return p;
    }
    private View scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2)); return scroll;
    }
    private LinearLayout card() { LinearLayout c = column(); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.setBackground(surface(PANEL, 22, LINE)); return c; }
    private void section(LinearLayout p, String title, String hint) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label(title, 15, INK); bold(t); r.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); r.addView(label(hint, 10, MUTED)); p.addView(r, gap(-2, 24));
    }
    private void note(LinearLayout p, String text) { TextView t = label(text, 11, MUTED); t.setLineSpacing(dp(5), 1); p.addView(t, gap(-2, 16)); }
    private void infoRow(LinearLayout p, String key, String value) {
        LinearLayout r = row(); TextView k = label(key, 12, MUTED); r.addView(k, new LinearLayout.LayoutParams(0, -2, 1)); r.addView(label(value, 12, INK)); p.addView(r, gap(-2, 9));
    }
    private TextView label(String value, float size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(color); v.setTextSize(size); v.setIncludeFontPadding(false); return v;
    }
    private void bold(TextView v) { v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
    private Button primary(String text) { return button(text, ACCENT, BG); }
    private Button secondary(String text) { return button(text, PANEL, INK); }
    private Button button(String text, int background, int ink) {
        Button b = new Button(this); b.setText(text); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(ink);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setIncludeFontPadding(false); b.setPadding(dp(8), dp(6), dp(8), dp(6)); b.setGravity(Gravity.CENTER);
        bold(b); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226D8E54), surface(background, 15, background == PANEL ? LINE : 0), null));
        return b;
    }
    private void selected(Button b, boolean active) {
        b.setTextColor(active ? ACCENT : MUTED); b.setSelected(active);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226D8E54), surface(active ? 0xFF34432D : PANEL, 12, active ? 0xFF536849 : LINE), null));
    }
    private Switch toggle(String text, boolean checked) {
        Switch s = new Switch(this); s.setText(text); s.setTextSize(14); s.setTextColor(INK); s.setSwitchMinWidth(dp(42)); s.setSwitchPadding(dp(12));
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, MUTED}));
        s.setTrackTintList(new ColorStateList(states, new int[]{0xFF536849, LINE})); s.setChecked(checked); return s;
    }
    private GradientDrawable surface(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams full(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private LinearLayout.LayoutParams gap(int height, int top) { LinearLayout.LayoutParams lp = full(height); lp.topMargin = dp(top); return lp; }
    private LinearLayout.LayoutParams weighted(int height) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height < 0 ? height : dp(height), 1); lp.setMargins(dp(3), 0, dp(3), 0); return lp; }
}
