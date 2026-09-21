package io.github.jvmmw.debug;

import com.badlogic.gdx.Gdx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named timings for F3 Dump and the F4 overlay. Frame sections show steady
 * fps. Slow spans stay around so a multi-second cell enter is still visible
 * after the overlay settles. Spans nest on one thread.
 */
public final class PerfTrace {
    private static final long SLOW_NS = 50_000_000L;
    private static final long LOG_NS = 100_000_000L;
    private static final long HUD_NS = 30_000_000_000L;
    private static final int SLOW_KEEP = 16;

    private static final ThreadLocal<ArrayDeque<Open>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<String, Stat> stats = new ConcurrentHashMap<>();
    private static final Map<String, Long> frameNs = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastFrame = new ConcurrentHashMap<>();
    private static final Map<String, Long> loadNs = new ConcurrentHashMap<>();
    private static final String[] LOAD_SUM = {
        "load.parse", "load.dispose", "load.land", "load.place", "load.cook",
        "load.prep", "load.adopt", "walk.parse"
    };
    private static final ArrayDeque<Slow> slow = new ArrayDeque<>();
    private static Thread glThread;
    private static volatile boolean loading;
    private static String loadLabel = "";

    private PerfTrace() {
    }

    /** Start of a cell enter. Totals add up until {@link #finishLoad}. */
    public static void beginLoad(String label) {
        loadNs.clear();
        loadLabel = label == null ? "" : label;
        loading = true;
    }

    /** The overlay is done. Logs the sums and keeps them for the next Dump. */
    public static void finishLoad() {
        if (!loading) {
            return;
        }
        loading = false;
        if (Gdx.app == null) {
            return;
        }
        String line = loadLine();
        if (!line.isEmpty()) {
            Gdx.app.log("JVM-MW", "perf load " + line);
        }
    }

    /** Call at the start of a GL frame so the next {@code sealFrame} is that frame only. */
    public static void beginFrame() {
        glThread = Thread.currentThread();
        frameNs.clear();
    }

    /** Freeze this frame’s spans for Dump. Call at the end of the GL frame. */
    public static void sealFrame() {
        lastFrame.clear();
        lastFrame.putAll(frameNs);
        if (Gdx.app == null) {
            return;
        }
        long worst = 0L;
        for (long v : lastFrame.values()) {
            if (v > worst) {
                worst = v;
            }
        }
        if (worst >= LOG_NS) {
            Gdx.app.log("JVM-MW", "perf frame " + breakdown(lastFrame));
        }
    }

    public static void begin(String name) {
        Open open = new Open();
        open.name = name;
        open.start = System.nanoTime();
        STACK.get().push(open);
    }

    /** Extra label on the current span, kept only if that span is slow. */
    public static void detail(String extra) {
        ArrayDeque<Open> stack = STACK.get();
        if (!stack.isEmpty() && extra != null && !extra.isEmpty()) {
            stack.peek().detail = extra;
        }
    }

    public static void end() {
        ArrayDeque<Open> stack = STACK.get();
        if (stack.isEmpty()) {
            return;
        }
        Open open = stack.pop();
        record(open.name, System.nanoTime() - open.start, open.detail, Thread.currentThread() == glThread);
    }

    /** A finished chunk from a worker, or a frame section already timed elsewhere. */
    public static void add(String name, long nanos) {
        record(name, nanos, null, Thread.currentThread() == glThread);
    }

    public static String hudLine() {
        Slow best = null;
        long now = System.nanoTime();
        synchronized (slow) {
            for (Slow s : slow) {
                if (now - s.at > HUD_NS) {
                    continue;
                }
                if (best == null || s.ns > best.ns) {
                    best = s;
                }
            }
        }
        if (best == null) {
            return "";
        }
        return "slow " + best.label + " " + ms(best.ns) + "ms";
    }

    public static void appendDump(StringBuilder sb) {
        sb.append("traceFrame");
        String parts = breakdown(lastFrame);
        sb.append(parts.isEmpty() ? " none" : " " + parts);
        sb.append('\n');

        List<Stat> top = new ArrayList<>(stats.values());
        top.sort(Comparator.comparingLong((Stat s) -> s.max).reversed());
        sb.append("traceMax");
        int shown = 0;
        for (Stat s : top) {
            if (s.max < SLOW_NS) {
                continue;
            }
            sb.append(' ').append(s.name).append('=').append(ms(s.max));
            if (++shown >= 8) {
                break;
            }
        }
        if (shown == 0) {
            sb.append(" none");
        }
        sb.append('\n');

        sb.append("traceLoad");
        String load = loadLine();
        sb.append(load.isEmpty() ? " none" : " " + load);
        sb.append('\n');

        sb.append("traceSlow");
        synchronized (slow) {
            if (slow.isEmpty()) {
                sb.append(" none\n");
                return;
            }
            shown = 0;
            for (Slow s : slow) {
                sb.append(' ').append(s.label).append('=').append(ms(s.ns));
                if (++shown >= 8) {
                    break;
                }
            }
        }
        sb.append('\n');
    }

    private static void record(String name, long nanos, String detail, boolean frame) {
        if (name == null || nanos < 0L) {
            return;
        }
        stats.computeIfAbsent(name, Stat::new).add(nanos);
        if (frame) {
            frameNs.merge(name, nanos, Long::sum);
        }
        if (loading && loadKey(name)) {
            loadNs.merge(name, nanos, Long::sum);
        }
        if (nanos < SLOW_NS) {
            return;
        }
        String label = detail == null || detail.isEmpty() ? name : name + " " + detail;
        synchronized (slow) {
            slow.addFirst(new Slow(label, nanos, System.nanoTime()));
            while (slow.size() > SLOW_KEEP) {
                slow.removeLast();
            }
        }
        if (nanos >= LOG_NS && Gdx.app != null) {
            Gdx.app.log("JVM-MW", "perf " + label + " " + ms(nanos) + "ms");
        }
    }

    private static boolean loadKey(String name) {
        return (name.startsWith("load.") && !"load.pump".equals(name))
            || name.startsWith("prep.")
            || "walk.parse".equals(name)
            || "bullet.rebuild".equals(name);
    }

    /** Sums for the last cell enter. Nested names are listed too; {@code sum} skips those. */
    private static String loadLine() {
        if (loadNs.isEmpty()) {
            return "";
        }
        long sum = 0L;
        for (String key : LOAD_SUM) {
            Long v = loadNs.get(key);
            if (v != null) {
                sum += v;
            }
        }
        String parts = breakdown(loadNs);
        StringBuilder sb = new StringBuilder();
        if (!loadLabel.isEmpty()) {
            sb.append(loadLabel).append(' ');
        }
        if (!parts.isEmpty()) {
            sb.append(parts).append(' ');
        }
        sb.append("sum=").append(ms(sum));
        return sb.toString();
    }

    /** Top chunks at least 0.3ms, biggest first. Nested spans overlap, so the numbers are not a partition. */
    private static String breakdown(Map<String, Long> map) {
        List<Map.Entry<String, Long>> frame = new ArrayList<>(map.entrySet());
        frame.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Long> e : frame) {
            if (e.getValue() < 300_000L) {
                continue;
            }
            if (shown > 0) {
                sb.append(' ');
            }
            sb.append(e.getKey()).append('=').append(ms(e.getValue()));
            if (++shown >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private static String ms(long nanos) {
        return String.format(Locale.US, "%.0f", nanos / 1_000_000f);
    }

    private static final class Open {
        String name;
        String detail;
        long start;
    }

    private static final class Slow {
        final String label;
        final long ns;
        final long at;

        Slow(String label, long ns, long at) {
            this.label = label;
            this.ns = ns;
            this.at = at;
        }
    }

    private static final class Stat {
        final String name;
        long total;
        long max;
        int count;

        Stat(String name) {
            this.name = name;
        }

        synchronized void add(long nanos) {
            total += nanos;
            count++;
            if (nanos > max) {
                max = nanos;
            }
        }
    }
}
