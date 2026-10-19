package com.workbuddy.backscreen;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Bounded, checksummed frame snapshot used across process restarts. */
final class FrameArchive {
    private static final int MAGIC = 0x42534632, MAX_FRAMES = 4096;
    final List<int[]> frames;
    final int start;
    private FrameArchive(List<int[]> frames, int start) { this.frames = frames; this.start = start; }
    static void write(OutputStream stream, List<int[]> frames, int start) throws IOException {
        if (frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES
                || start < 0 || start >= frames.size()) throw new IOException("Invalid frame sequence");
        DataOutputStream out = new DataOutputStream(stream);
        out.writeInt(MAGIC); out.writeInt(frames.size()); out.writeInt(start);
        CRC32 crc = new CRC32();
        for (int[] frame : frames) {
            if (frame == null || frame.length != BackScreenApi.N) throw new IOException("Invalid frame length");
            for (int i = 0; i < frame.length; i++) {
                int v = BackScreenApi.DOT_OK[i] ? Math.max(0, Math.min(255, frame[i])) : 0;
                out.writeByte(v); crc.update(v);
            }
        }
        out.writeLong(crc.getValue()); out.flush();
    }
    static FrameArchive read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        if (in.readInt() != MAGIC) throw new IOException("Invalid frame format");
        int count = in.readInt(), start = in.readInt();
        if (count < 1 || count > MAX_FRAMES || start < 0 || start >= count) throw new IOException("Invalid frame count");
        List<int[]> frames = new ArrayList<>(count); CRC32 crc = new CRC32();
        for (int f = 0; f < count; f++) {
            int[] frame = new int[BackScreenApi.N];
            for (int i = 0; i < frame.length; i++) {
                int v = in.readUnsignedByte(); crc.update(v);
                if (!BackScreenApi.DOT_OK[i] && v != 0) throw new IOException("Invalid LED mask");
                frame[i] = v;
            }
            frames.add(frame);
        }
        if (in.readLong() != crc.getValue() || in.read() != -1) throw new IOException("Damaged frame snapshot");
        return new FrameArchive(frames, start);
    }
}
