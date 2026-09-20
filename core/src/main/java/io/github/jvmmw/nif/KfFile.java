/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A Morrowind animation file: named bone tracks plus labels like idle or
 * containeropen. NPCs and chests sample these.
 */
public final class KfFile {
    public final String filename;
    public final List<TextKey> textKeys = new ArrayList<>();
    public final Map<String, BoneTrack> tracks = new LinkedHashMap<>();

    public static final class TextKey {
        public final float time;
        public final String text;

        public TextKey(float time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public static final class BoneTrack {
        public final String bone;
        public final NiKeyframeController controller;
        public final NiKeyframeData data;

        public BoneTrack(String bone, NiKeyframeController controller, NiKeyframeData data) {
            this.bone = bone;
            this.controller = controller;
            this.data = data;
        }
    }

    public static final class IdleLoop {
        public float startTime;
        public float stopTime;
        public float loopStart;
        public float loopStop;
        public float time;
    }

    private KfFile(String filename) {
        this.filename = filename;
    }

    public static KfFile load(NifFile nif, String filename) {
        KfFile kf = new KfFile(filename);
        NiSequenceStreamHelper seq = null;
        for (int root : nif.roots) {
            NifRecord rec = nif.get(root);
            if (rec instanceof NiSequenceStreamHelper helper) {
                seq = helper;
                break;
            }
        }
        if (seq == null) {
            return kf;
        }
        List<NifRecord> extras = extraList(nif, seq.extra);
        if (extras.isEmpty() || !(extras.get(0) instanceof NiTextKeyExtraData text)) {
            return kf;
        }
        for (NiTextKeyExtraData.Key key : text.keys) {
            for (String line : key.text.split("\\r\\n|\\n|\\r")) {
                String trimmed = line.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    kf.textKeys.add(new TextKey(key.time, trimmed));
                }
            }
        }
        int ctrl = seq.controller;
        for (int i = 1; i < extras.size() && ctrl >= 0; i++) {
            NifRecord extra = extras.get(i);
            NifRecord ctrlRec = nif.get(ctrl);
            if (!(ctrlRec instanceof NiKeyframeController keyCtrl)) {
                break;
            }
            ctrl = keyCtrl.next;
            if (!(extra instanceof NiStringExtraData str)) {
                continue;
            }
            NifRecord dataRec = nif.get(keyCtrl.data);
            if (!(dataRec instanceof NiKeyframeData data)) {
                continue;
            }
            kf.tracks.putIfAbsent(str.data.toLowerCase(Locale.ROOT), new BoneTrack(str.data, keyCtrl, data));
        }
        return kf;
    }

    public IdleLoop playIdle() {
        return play("idle", "start", "stop", true);
    }

    public IdleLoop play(String group, String start, String stop, boolean loopFallback) {
        int groupEnd = -1;
        for (int i = textKeys.size() - 1; i >= 0; i--) {
            if (isGroupKey(textKeys.get(i).text, group)) {
                groupEnd = i;
                break;
            }
        }
        if (groupEnd < 0) {
            return null;
        }
        int startIdx = -1;
        int stopIdx = -1;
        for (int i = groupEnd; i >= 0; i--) {
            if (startIdx < 0 && equalsEvent(textKeys.get(i).text, group, start)) {
                startIdx = i;
            }
            if (stopIdx < 0 && equalsEvent(textKeys.get(i).text, group, stop)) {
                stopIdx = i;
            }
            if (startIdx >= 0 && stopIdx >= 0) {
                break;
            }
        }
        if (startIdx < 0 || stopIdx < 0 || textKeys.get(startIdx).time > textKeys.get(stopIdx).time) {
            return null;
        }
        IdleLoop loop = new IdleLoop();
        loop.startTime = textKeys.get(startIdx).time;
        loop.stopTime = textKeys.get(stopIdx).time;
        loop.loopStart = loop.startTime;
        loop.loopStop = loopFallback ? loop.stopTime : Float.MAX_VALUE;
        loop.time = loop.startTime;
        // OpenMW picks up Loop Start/Stop while playing. Skipping keys after
        // startTime meant walkforward wrapped at Stop (a plant-foot pose).
        for (int i = startIdx; i <= groupEnd; i++) {
            TextKey key = textKeys.get(i);
            if (equalsEvent(key.text, group, "loop start")) {
                loop.loopStart = key.time;
            } else if (loopFallback && equalsEvent(key.text, group, "loop stop")) {
                loop.loopStop = key.time;
            }
        }
        return loop;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("kf=").append(filename).append(" tracks=").append(tracks.size()).append('\n');
        Map<String, float[]> groups = new TreeMap<>();
        for (TextKey key : textKeys) {
            int sep = key.text.indexOf(": ");
            String group = sep < 0 ? key.text : key.text.substring(0, sep);
            float[] span = groups.computeIfAbsent(group, g -> new float[] {key.time, key.time});
            span[0] = Math.min(span[0], key.time);
            span[1] = Math.max(span[1], key.time);
        }
        for (Map.Entry<String, float[]> e : groups.entrySet()) {
            sb.append("  group=").append(e.getKey())
                .append(" t=").append(fmt(e.getValue()[0])).append("..").append(fmt(e.getValue()[1]))
                .append('\n');
        }
        int n = 0;
        for (String bone : tracks.keySet()) {
            if (n++ >= 12) {
                sb.append("  ... ").append(tracks.size() - 12).append(" more bones\n");
                break;
            }
            sb.append("  bone=").append(bone).append('\n');
        }
        return sb.toString();
    }

    private static List<NifRecord> extraList(NifFile nif, int first) {
        List<NifRecord> list = new ArrayList<>();
        for (int idx = first; idx >= 0; ) {
            NifRecord rec = nif.get(idx);
            if (rec == null) {
                break;
            }
            list.add(rec);
            idx = rec.extra;
        }
        return list;
    }

    private static boolean isGroupKey(String text, String group) {
        return text.startsWith(group) && text.startsWith(": ", group.length());
    }

    private static boolean equalsEvent(String text, String group, String event) {
        String want = group + ": " + event;
        return text.length() >= want.length() && text.startsWith(want);
    }

    private static String fmt(float v) {
        if (v == (int) v) {
            return Integer.toString((int) v);
        }
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
