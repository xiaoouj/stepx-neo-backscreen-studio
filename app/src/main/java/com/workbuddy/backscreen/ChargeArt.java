package com.workbuddy.backscreen;

/** Number-first battery face, laid out for the physical 17 × 23 LED mask. */
public final class ChargeArt {
    public static final int STYLE_COUNT = 3;
    private ChargeArt() {}
    // Five by seven numerals stay the same size at 9 → 10 and 99 → 100.
    private static final String[] DIGITS = {
        "01110/10001/10001/10001/10001/10001/01110",
        "00100/01100/00100/00100/00100/00100/01110",
        "01110/10001/00001/00010/00100/01000/11111",
        "11110/00001/00001/01110/00001/00001/11110",
        "00010/00110/01010/10010/11111/00010/00010",
        "11111/10000/10000/11110/00001/00001/11110",
        "01110/10000/10000/11110/10001/10001/01110",
        "11111/00001/00010/00100/01000/01000/01000",
        "01110/10001/10001/01110/10001/10001/01110",
        "01110/10001/10001/01111/00001/00001/01110"
    };

    private static final String[] SMALL = {
        "111/101/101/101/111", "010/110/010/010/111", "111/001/111/100/111",
        "111/001/111/001/111", "101/101/111/001/001", "111/100/111/001/111",
        "111/100/111/101/111", "111/001/010/010/010", "111/101/111/101/111",
        "111/101/111/001/111"
    };
    private static final int[][] RING = {
        {8,1},{9,1},{10,1},{11,2},{12,2},{13,3},{14,4},{14,5},{15,6},{15,7},
        {15,8},{15,9},{15,10},{14,11},{14,12},{13,13},{12,14},{11,14},{10,15},{9,15},
        {8,15},{7,15},{6,15},{5,14},{4,14},{3,13},{2,12},{2,11},{1,10},{1,9},
        {1,8},{1,7},{1,6},{2,5},{2,4},{3,3},{4,2},{5,2},{6,1},{7,1}
    };

    public static int[] frame(int level, boolean charging, int phase) {
        return frame(level, charging, phase, 0);
    }

    public static int[] frame(int level, boolean charging, int phase, int style) {
        level = Math.max(0, Math.min(100, level));
        int[] out = new int[BackScreenApi.N];
        int pulse = Math.floorMod(phase, 8);
        int ink = charging && level < 100 ? 175 + (pulse < 4 ? pulse : 7 - pulse) * 22 : 230;
        if (style == 1) {
            int edge = charging && level < 100 ? ink : 150;
            for (int x = 7; x <= 9; x++) dot(out, x, 0, edge);
            for (int x = 5; x <= 11; x++) { dot(out, x, 2, edge); dot(out, x, 13, edge); }
            for (int y = 3; y <= 12; y++) { dot(out, 4, y, edge); dot(out, 12, y, edge); }
            int rows = (level * 8 + 99) / 100;
            for (int row = 0; row < rows; row++)
                for (int x = 6; x <= 10; x++) dot(out, x, 11 - row, 180);
            compactLabel(out, level, true, 16);
            return out;
        }
        if (style == 2) {
            int count = (level * RING.length + 99) / 100;
            for (int i = 0; i < RING.length; i++) dot(out, RING[i][0], RING[i][1], i < count ? ink : 28);
            compactLabel(out, level, false, 6);
            glyph(out, "11001/11010/00100/01011/10011", 6, 17, 170);
            return out;
        }
        // Solid battery; the empty portion remains dark while charging.
        for (int x = 3; x <= 12; x++) { dot(out, x, 1, 110); dot(out, x, 5, 110); }
        for (int y = 2; y <= 4; y++) { dot(out, 2, y, 110); dot(out, 13, y, 110); }
        dot(out, 14, 3, 110);
        int columns = (level + 9) / 10;
        for (int x = 0; x < columns; x++)
            for (int y = 2; y <= 4; y++) dot(out, 3 + x, y, ink);
        String value = Integer.toString(level);
        int left = (BackScreenApi.W - (value.length() * 6 - 1)) / 2;
        for (int i = 0; i < value.length(); i++)
            glyph(out, DIGITS[value.charAt(i) - '0'], left + i * 6, 8, 255);
        glyph(out, "11001/11010/00100/01011/10011", 6, 17, 170);
        if (charging && level < 100) glyph(out, "001/011/111/110/100", 1, 17, ink);
        return out;
    }
    private static void compactLabel(int[] out, int level, boolean unit, int top) {
        String value = Integer.toString(level);
        int width = -1;
        for (int i = 0; i < value.length(); i++) width += (value.charAt(i) == '1' ? 1 : 3) + 1;
        if (unit) width += 6;
        int x = (BackScreenApi.W - width) / 2;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(i) - '0';
            glyph(out, digit == 1 ? "1/1/1/1/1" : SMALL[digit], x, top, 255);
            x += (digit == 1 ? 1 : 3) + 1;
        }
        if (unit) glyph(out, "11001/11010/00100/01011/10011", x, top, 170);
    }
    private static void glyph(int[] out, String bitmap, int left, int top, int ink) {
        int x = 0, y = 0;
        for (int i = 0; i < bitmap.length(); i++) {
            char c = bitmap.charAt(i);
            if (c == '/') { x = 0; y++; }
            else { if (c == '1') dot(out, left + x, top + y, ink); x++; }
        }
    }
    private static void dot(int[] out, int x, int y, int ink) {
        if (x >= 0 && x < BackScreenApi.W && y >= 0 && y < BackScreenApi.H
                && BackScreenApi.DOT_OK[y * BackScreenApi.W + x])
            out[y * BackScreenApi.W + x] = ink;
    }
}
