package io.github.jvmmw.debug;

import java.util.Arrays;
import java.util.Locale;

/**
 * CPU section timers and draw counters for F3 / the perf overlay.
 * Rewrite of the idea of OpenMW {@code Resource::Profiler} / osg F3 stats — not OSG.
 */
public final class FrameProfiler {
    public static final int WALK_STEP = 0;
    public static final int UPDATE = 1;
    public static final int RTT_REFRACT = 2;
    public static final int RTT_REFLECT = 3;
    public static final int SKY = 4;
    public static final int TERRAIN = 5;
    public static final int OPAQUE = 6;
    public static final int WATER = 7;
    public static final int ALPHA = 8;
    public static final int HUD = 9;
    public static final int FRAME = 10;
    public static final int SECTION_COUNT = 11;
    private static final int WINDOW = 60;
    private static final String[] NAMES = {
        "walkStep", "update", "rttRefract", "rttReflect", "sky", "terrain", "opaque", "water", "alpha", "hud"
    };

    private final long[] sectionStart = new long[SECTION_COUNT];
    private final long[] sectionNs = new long[SECTION_COUNT];
    private final long[] lastSectionNs = new long[SECTION_COUNT];
    private final float[] frameHist = new float[WINDOW];
    private int histIndex;
    private int histCount;

    private int draws;
    private int tris;
    private int drawsTerrain;
    private int drawsOpaque;
    private int drawsAlpha;
    private int drawsRtt;
    private boolean rtt;

    public int lastDraws;
    public int lastTris;
    public int lastDrawsTerrain;
    public int lastDrawsOpaque;
    public int lastDrawsAlpha;
    public int lastDrawsRtt;
    public int meshes;
    public int placed;
    public int npc;
    public int crea;
    public int lights;
    public int landTiles;
    public float fps;
    public float frameMs;
    public float frameMaxMs;
    public float walkParseMs;
    public float walkGpuMs;
    public float walkSwapMs;
    public boolean hadWalk;

    public void beginFrame() {
        Arrays.fill(sectionNs, 0L);
        draws = 0;
        tris = 0;
        drawsTerrain = 0;
        drawsOpaque = 0;
        drawsAlpha = 0;
        drawsRtt = 0;
        rtt = false;
        begin(FRAME);
    }

    public void endFrame() {
        end(FRAME);
        System.arraycopy(sectionNs, 0, lastSectionNs, 0, SECTION_COUNT);
        lastDraws = draws;
        lastTris = tris;
        lastDrawsTerrain = drawsTerrain;
        lastDrawsOpaque = drawsOpaque;
        lastDrawsAlpha = drawsAlpha;
        lastDrawsRtt = drawsRtt;
        float nowMs = sectionNs[FRAME] / 1_000_000f;
        frameHist[histIndex] = nowMs;
        histIndex = (histIndex + 1) % WINDOW;
        if (histCount < WINDOW) {
            histCount++;
        }
        float sum = 0f;
        float max = 0f;
        for (int i = 0; i < histCount; i++) {
            sum += frameHist[i];
            if (frameHist[i] > max) {
                max = frameHist[i];
            }
        }
        frameMs = sum / histCount;
        frameMaxMs = max;
        fps = frameMs > 0.01f ? 1000f / frameMs : 0f;
    }

    public void begin(int section) {
        sectionStart[section] = System.nanoTime();
    }

    public void end(int section) {
        sectionNs[section] += System.nanoTime() - sectionStart[section];
    }

    public void setRtt(boolean drawingRtt) {
        rtt = drawingRtt;
    }

    /** {@code meshPass}: 1 terrain, 0 opaque, 2 alpha, {@code -1} water surface. */
    public void addDraw(int meshPass, int indexCount) {
        int triangles = indexCount / 3;
        if (rtt) {
            drawsRtt++;
            return;
        }
        draws++;
        tris += triangles;
        if (meshPass == 1) {
            drawsTerrain++;
        } else if (meshPass == 2) {
            drawsAlpha++;
        } else if (meshPass == 0) {
            drawsOpaque++;
        }
    }

    public void addWalkGpuNs(long nanos) {
        walkGpuMs += nanos / 1_000_000f;
        hadWalk = true;
    }

    public void setWalkParseNs(long nanos) {
        walkParseMs = nanos / 1_000_000f;
        hadWalk = true;
    }

    public void setWalkSwapNs(long nanos) {
        walkSwapMs = nanos / 1_000_000f;
        hadWalk = true;
    }

    public void resetWalk() {
        walkParseMs = 0f;
        walkGpuMs = 0f;
        walkSwapMs = 0f;
    }

    public float sectionMs(int section) {
        return lastSectionNs[section] / 1_000_000f;
    }

    public String fattestName() {
        return NAMES[fattestIndex()];
    }

    public String hudText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%.0f fps  %.1f ms  max %.1f  n=%d%n",
            fps, frameMs, frameMaxMs, histCount));
        sb.append("draws ").append(lastDraws).append("  rtt ").append(lastDrawsRtt).append('\n');
        sb.append("fat ").append(fattestName()).append(' ')
            .append(String.format(Locale.US, "%.1f", sectionMs(fattestIndex())));
        if (hadWalk) {
            sb.append('\n').append(String.format(Locale.US, "walk gpu %.0f  parse %.0f  swap %.0f",
                walkGpuMs, walkParseMs, walkSwapMs));
        }
        return sb.toString();
    }

    public void appendDump(StringBuilder sb) {
        sb.append(String.format(Locale.US, "fps=%.1f frameMs=%.1f frameMaxMs=%.1f fpsSamples=%d%n",
            fps, frameMs, frameMaxMs, histCount));
        sb.append("ms");
        for (int i = 0; i < NAMES.length; i++) {
            if (i > 0) {
                sb.append(" ms");
            }
            sb.append('.').append(NAMES[i]).append('=').append(String.format(Locale.US, "%.1f", sectionMs(i)));
        }
        sb.append('\n');
        sb.append("draws=").append(lastDraws)
            .append(" tris=").append(lastTris)
            .append(" draws.terrain=").append(lastDrawsTerrain)
            .append(" draws.opaque=").append(lastDrawsOpaque)
            .append(" draws.alpha=").append(lastDrawsAlpha)
            .append(" draws.rtt=").append(lastDrawsRtt)
            .append('\n');
        sb.append("meshes=").append(meshes)
            .append(" placed=").append(placed)
            .append(" npc=").append(npc)
            .append(" crea=").append(crea)
            .append(" lights=").append(lights)
            .append(" landTiles=").append(landTiles)
            .append('\n');
        if (hadWalk) {
            sb.append(String.format(Locale.US, "walkParseMs=%.0f walkGpuMs=%.0f walkSwapMs=%.0f%n",
                walkParseMs, walkGpuMs, walkSwapMs));
        }
    }

    private int fattestIndex() {
        int best = UPDATE;
        long bestNs = -1L;
        for (int i = 0; i < NAMES.length; i++) {
            if (lastSectionNs[i] > bestNs) {
                bestNs = lastSectionNs[i];
                best = i;
            }
        }
        return best;
    }
}
