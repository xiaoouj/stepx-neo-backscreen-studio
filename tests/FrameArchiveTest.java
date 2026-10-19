package com.workbuddy.backscreen;

import java.io.*;
import java.util.*;

public final class FrameArchiveTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejected(byte[] bytes) throws Exception {
        try { FrameArchive.read(new ByteArrayInputStream(bytes)); throw new AssertionError("Damaged snapshot accepted"); }
        catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        List<int[]> frames = PixelArt.get("heart_beat");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); FrameArchive.write(out, frames, 1);
        byte[] bytes = out.toByteArray(); FrameArchive saved = FrameArchive.read(new ByteArrayInputStream(bytes));
        check(saved.start == 1 && saved.frames.size() == 2, "Playback position/count lost");
        for (int i = 0; i < frames.size(); i++) check(Arrays.equals(frames.get(i),saved.frames.get(i)), "Frame data changed");
        // A new reader has no dependency on any static or activity state.
        check(FrameArchive.read(new ByteArrayInputStream(bytes)).frames.size() == 2, "Cannot independently restore");
        rejected(Arrays.copyOf(bytes,bytes.length-1));
        byte[] changed = bytes.clone(); changed[12+17*10+8] ^= 1; rejected(changed);
        changed = bytes.clone(); changed[4] = 127; rejected(changed);
        changed = bytes.clone(); changed[0] = 0; rejected(changed);
        changed = Arrays.copyOf(bytes,bytes.length+1); rejected(changed);
        out.reset(); FrameArchive.write(out, Collections.singletonList(frames.get(0)),0);
        check(FrameArchive.read(new ByteArrayInputStream(out.toByteArray())).frames.size()==1, "Static image restore");
        try { FrameArchive.write(new ByteArrayOutputStream(),Collections.emptyList(),0); throw new AssertionError("Empty sequence accepted"); }
        catch(IOException expected) { }
        try { FrameArchive.write(new ByteArrayOutputStream(),frames,2); throw new AssertionError("Bad starting frame accepted"); }
        catch(IOException expected) { }
        System.out.println("PASS: static/animated frame recovery, start index, truncation, corruption and bounds checks.");
    }
}
