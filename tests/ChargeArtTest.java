package com.workbuddy.backscreen;

import java.util.*;

/** JVM checks use the production renderer and production physical LED mask. */
public final class ChargeArtTest {
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
    private static String numerals(int[] frame) {
        return Arrays.toString(Arrays.copyOfRange(frame, 8 * 17, 15 * 17));
    }
    private static String numeralRegion(int[] frame, int style) {
        StringBuilder out = new StringBuilder();
        int top = style == 0 ? 8 : style == 1 ? 16 : 6;
        int bottom = style == 0 ? 14 : top + 4;
        int left = style == 2 ? 3 : 0, right = style == 2 ? 13 : 16;
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) out.append(frame[y*17+x]).append(',');
        return out.toString();
    }
    public static void main(String[] args) {
        Set<String> values = new HashSet<>();
        int previous = -1;
        for (int level = 0; level <= 100; level++) {
            int[] still = ChargeArt.frame(level, false, 0);
            check(values.add(numerals(still)), "Ambiguous numeral at " + level);
            int gauge = 0;
            for (int y = 2; y <= 4; y++) for (int x = 3; x <= 12; x++)
                if (still[y * 17 + x] > 0) gauge++;
            check(gauge >= previous, "Battery gauge decreases at " + level);
            check(level != 0 || gauge == 0, "Empty battery must have no fill");
            check(level != 100 || gauge == 30, "Full battery must fill the gauge");
            previous = gauge;
            for (int phase : new int[]{-17, -1, 0, 1, 3, 7, 8, 100, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                for (boolean charging : new boolean[]{false, true}) {
                    int[] frame = ChargeArt.frame(level, charging, phase);
                    check(frame.length == 391, "Wrong frame size");
                    for (int i = 0; i < frame.length; i++) {
                        check(frame[i] >= 0 && frame[i] <= 255, "Invalid brightness");
                        check(BackScreenApi.DOT_OK[i] || frame[i] == 0, "Dead corner lit");
                    }
                    check(numerals(frame).equals(numerals(still)), "Animation changes digits");
                    if (!charging || level == 100) check(Arrays.equals(frame, still), "Static face animates");
                    if (level == 0) for (int y = 2; y <= 4; y++) for (int x = 3; x <= 12; x++)
                        check(frame[y * 17 + x] == 0, "Charging illuminates empty capacity");
                }
            }
        }
        check(Arrays.equals(ChargeArt.frame(-8, false, 0), ChargeArt.frame(0, false, 0)), "Low clamp");
        check(Arrays.equals(ChargeArt.frame(108, false, 0), ChargeArt.frame(100, false, 0)), "High clamp");
        check(!Arrays.equals(ChargeArt.frame(50, true, 0), ChargeArt.frame(50, true, 3)), "No charging cue");
        for (int style = 0; style < ChargeArt.STYLE_COUNT; style++) {
            Set<String> labels = new HashSet<>();
            for (int level = 0; level <= 100; level++) {
                int[] still = ChargeArt.frame(level, false, 0, style);
                String label = numeralRegion(still, style);
                check(labels.add(label), "Ambiguous number in style " + style + " at " + level);
                for (int phase = -1; phase < 9; phase++) {
                    int[] frame = ChargeArt.frame(level, true, phase, style);
                    for (int i = 0; i < frame.length; i++) {
                        check(frame[i] >= 0 && frame[i] <= 255, "Invalid brightness in style " + style);
                        check(BackScreenApi.DOT_OK[i] || frame[i] == 0, "Masked LED in style " + style);
                    }
                    check(numeralRegion(frame, style).equals(label), "Animation obscures number in style " + style);
                    if (level == 100) check(Arrays.equals(frame, still), "Full face animates in style " + style);
                }
            }
        }
        for (String preset : new String[]{"heart_beat", "smiley", "star_blink", "loading", "note", "spade"}) {
            List<int[]> frames = PixelArt.get(preset);
            check(!frames.isEmpty(), "Missing preset " + preset);
            for (int[] frame : frames) {
                check(frame.length == 391, "Preset dimension");
                for (int i = 0; i < frame.length; i++) {
                    check(frame[i] >= 0 && frame[i] <= 255, "Preset brightness");
                    check(BackScreenApi.DOT_OK[i] || frame[i] == 0, "Preset clipped by physical corners");
                }
                if (preset.equals("heart_beat") || preset.equals("smiley"))
                    for (int y = 0; y < 23; y++) for (int x = 0; x < 17; x++)
                        check(frame[y*17+x] == frame[y*17+16-x], "Asymmetric " + preset);
            }
            frames.get(0)[0] = 255;
            check(PixelArt.get(preset).get(0)[0] == 0, "Library exposes mutable preset");
        }
        System.out.println("PASS: all 3 styles at 0–100%; masked corners, unique numbers, animation and 6 presets verified.");
        if (args.length > 0) {
            for (int level : new int[]{0, 5, 25, 50, 75, 99, 100}) {
                System.out.println(level + ":" + Arrays.toString(ChargeArt.frame(level, true, 3)));
            }
        }
    }
}
