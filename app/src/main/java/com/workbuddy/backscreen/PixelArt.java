package com.workbuddy.backscreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pixel-perfect, centered silhouettes drawn directly on the physical LED grid. */
public final class PixelArt {
    private static final Map<String, List<int[]>> LIB = new HashMap<>();
    static {
        LIB.put("heart_beat", art(
            new String[]{
                "..###...###..", ".#####.#####.", "#############", "#############",
                "#############", ".###########.", "..#########..", "...#######...",
                "....#####....", ".....###.....", "......#......"
            }, new String[]{
                ".##...##.", "####.####", "#########", "#########",
                ".#######.", "..#####..", "...###...", "....#...."
            }));
        String[] face = {
            "....#####....", "..##.....##..", ".#.........#.", ".#..#...#..#.",
            "#...#...#...#", "#...........#", "#...........#", "#..#.....#..#",
            "#...#####...#", ".#.........#.", ".#.........#.", "..##.....##..", "....#####...."
        };
        String[] blink = face.clone(); blink[3] = ".#.........#."; blink[4] = "#..##...##..#";
        LIB.put("smiley", art(face, face, face, face, face, blink));
        LIB.put("star_blink", star());
        LIB.put("loading", spinner());
        LIB.put("note", art(new String[]{
            "....#########", "....#.......#", "....#.......#", "....#########",
            "....#.......#", "....#.......#", "....#.......#", "....#.......#",
            "....#....####", "....#...#####", ".####...#####", "#####....###.",
            "#####........", ".###........."
        }));
        LIB.put("spade", art(new String[]{
            "......#......", ".....###.....", "....#####....", "...#######...",
            "..#########..", ".###########.", "#############", "#############",
            ".###########.", "..###.#.###..", "......#......", ".....###.....", "....#####...."
        }));
    }
    private static List<int[]> star() {
        List<int[]> frames = new ArrayList<>();
        double[] x = new double[10], y = new double[10];
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + 36 * i), r = i % 2 == 0 ? 7 : 3.1;
            x[i] = 8 + Math.cos(a) * r; y[i] = 11 + Math.sin(a) * r;
        }
        for (int ink : new int[]{255, 210, 150, 210}) {
            int[] frame = new int[BackScreenApi.N];
            for (int row = 3; row < 19; row++) for (int col = 0; col < 17; col++) {
                boolean inside = false;
                for (int i = 0, j = 9; i < 10; j = i++)
                    if ((y[i] > row) != (y[j] > row)
                            && col < (x[j]-x[i]) * (row-y[i]) / (y[j]-y[i]) + x[i]) inside = !inside;
                if (inside) frame[row*17+col] = ink;
            }
            // Preserve the uppermost tip, which lies exactly on the polygon boundary.
            frame[4*17+8] = ink; frames.add(frame);
        }
        return frames;
    }
    private static List<int[]> spinner() {
        int[][] dots = {{8,6},{12,7},{13,11},{12,15},{8,16},{4,15},{3,11},{4,7}};
        List<int[]> frames = new ArrayList<>();
        for (int phase = 0; phase < 8; phase++) {
            int[] frame = new int[BackScreenApi.N];
            for (int i = 0; i < dots.length; i++) {
                int age = Math.floorMod(phase-i, 8), ink = age == 0 ? 255 : age == 1 ? 125 : age == 2 ? 55 : 18;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                    if (Math.abs(dx) + Math.abs(dy) <= 1) frame[(dots[i][1]+dy)*17+dots[i][0]+dx] = ink;
            }
            frames.add(frame);
        }
        return frames;
    }
    private static List<int[]> art(String[]... bitmaps) {
        List<int[]> result = new ArrayList<>();
        for (String[] bitmap : bitmaps) {
            int width = bitmap[0].length(), height = bitmap.length;
            if (width > BackScreenApi.W || height > BackScreenApi.H) throw new IllegalArgumentException("Oversize art");
            int left = (BackScreenApi.W-width)/2, top = (BackScreenApi.H-height)/2;
            int[] frame = new int[BackScreenApi.N];
            for (int y = 0; y < height; y++) {
                if (bitmap[y].length() != width) throw new IllegalArgumentException("Unequal pixel rows");
                for (int x = 0; x < width; x++) if (bitmap[y].charAt(x) == '#') frame[(top+y)*17+left+x] = 255;
            }
            result.add(frame);
        }
        return result;
    }
    public static List<int[]> get(String key) {
        List<int[]> out = new ArrayList<>(); List<int[]> stored = LIB.get(key);
        if (stored != null) for (int[] frame : stored) out.add(frame.clone());
        return out;
    }
}
